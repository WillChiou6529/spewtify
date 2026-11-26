package app.vitune.android.tagging

import android.content.Context
import android.util.Log
import app.vitune.android.Database
import app.vitune.android.models.Song
import app.vitune.android.models.TagCount
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.requests.nextPage
import app.vitune.providers.tags.Tag
import app.vitune.providers.tags.TagKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

private const val TAG = "DailyMix"

@Serializable
data class MixSong(
    val id: String,
    val title: String,
    val artistsText: String? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    /** true＝自己的收藏；取消收藏後當天的 Mix 也要拿掉 */
    val own: Boolean = false
) {
    fun toSong() = Song(
        id = id,
        title = title,
        artistsText = artistsText,
        durationText = durationText,
        thumbnailUrl = thumbnailUrl
    )
}

/** 一個 Daily Mix：tag 是分類 id；matched = 確定符合該分類的首數（自己收藏的＋官方索引確認的推薦）。 */
@Serializable
data class DailyMix(
    val tag: String,
    val songs: List<MixSong>,
    val ownCount: Int,
    val matched: Int
)

@Serializable
private data class DailyMixCache(val day: Long, val mixes: List<DailyMix>)

/**
 * 首頁 Daily Mix（Spotify 式）：依收藏的曲風分群，每天產生一次。
 * 每個 Mix＝自己收藏裡該分類最常聽的歌（每天換一批）＋YouTube 電台推薦裡、經官方索引確認同分類的新歌。
 */
object DailyMixes {
    private const val FILE = "daily_mix.json"
    private const val MIN_SONGS = 5
    private const val MIN_MIXES = 2
    private const val MAX_MIXES = 6
    private const val OWN_POOL = 25
    private const val OWN_PER_MIX = 15
    private const val RECS_PER_MIX = 10
    private const val SEEDS_PER_MIX = 2
    private val json = Json { ignoreUnknownKeys = true }

    /** 背景 Worker 和首頁可能同時呼叫，一次只算一份。 */
    private val mutex = Mutex()

    /**
     * 首頁用：今天已經算好（通常是背景分類完順便算的）就直接用，只把取消收藏／封鎖的歌拿掉；
     * 還沒算才當場算。force＝分類結果變了，重算今天的。
     */
    suspend fun load(context: Context, force: Boolean = false): List<DailyMix> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val today = LocalDate.now().toEpochDay()
            val file = File(context.filesDir, FILE)
            val cached = catching { json.decodeFromString(DailyMixCache.serializer(), file.readText()) }.getOrNull()
            if (!force && cached != null && cached.day == today && cached.mixes.isNotEmpty())
                return@withLock prune(cached.mixes)

            val built = catching { build(context, today) }
                .onFailure { Log.w(TAG, "產生 Daily Mix 失敗", it) }
                .getOrNull()
                .orEmpty()
            if (built.isEmpty()) return@withLock prune(cached?.mixes.orEmpty())

            catching {
                val tmp = File(context.filesDir, "$FILE.tmp")
                tmp.writeText(json.encodeToString(DailyMixCache.serializer(), DailyMixCache(today, built)))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }.onFailure { Log.w(TAG, "Daily Mix 存檔失敗", it) }
            built
        }
    }

    /** 拿掉已取消收藏的自己的歌、被封鎖的歌；空掉的 Mix 不顯示。 */
    private suspend fun prune(mixes: List<DailyMix>): List<DailyMix> {
        if (mixes.isEmpty()) return mixes
        val liked = Database.likedSongsNow().mapTo(HashSet()) { it.id }
        val blocked = Database.blacklistedIds().toHashSet()
        return mixes.mapNotNull { mix ->
            val songs = mix.songs.filter { it.id !in blocked && (!it.own || it.id in liked) }
            if (songs.isEmpty()) null
            else mix.copy(songs = songs, ownCount = songs.count { it.own })
        }
    }

    /** 曲風優先；曲風不到 2 個時用心情補。 */
    internal fun chooseTags(counts: List<TagCount>): List<TagCount> {
        val usable = counts.filter { it.count >= MIN_SONGS }
        val genres = usable.filter { Tag.fromId(it.tag)?.kind == TagKind.GENRE }.sortedByDescending { it.count }
        val moods = usable.filter { Tag.fromId(it.tag)?.kind == TagKind.MOOD }.sortedByDescending { it.count }
        val chosen = genres.take(MAX_MIXES).toMutableList()
        if (chosen.size < MIN_MIXES) chosen += moods.take(MIN_MIXES - chosen.size)
        return chosen
    }

    private suspend fun build(context: Context, day: Long): List<DailyMix> {
        val chosen = chooseTags(Database.favoriteTagCountsNow())
        if (chosen.isEmpty()) return emptyList()
        val index = YtTagIndex.load(context)
        // 推薦不要出現已經收藏或封鎖的歌（不只該分類前 50 首）
        val exclude = Database.likedSongsNow().mapTo(HashSet()) { it.id } + Database.blacklistedIds()

        return chosen.map { tagCount ->
            val random = Random(day * 31 + tagCount.tag.hashCode())
            val pool = Database.songsWithTag(tagCount.tag, OWN_POOL * 2)
            // 前 25 首最常聽的裡面，每天隨機挑 15 首
            val own = pool.take(OWN_POOL).shuffled(random).take(OWN_PER_MIX)
            // 單一 Mix 的推薦失敗（網路逾時）只少推薦，不影響其他 Mix
            val recs = if (index == null) emptyList()
            else catching {
                recommend(
                    seeds = own.shuffled(random).take(SEEDS_PER_MIX),
                    tagId = tagCount.tag,
                    index = index,
                    exclude = exclude
                )
            }.onFailure { Log.w(TAG, "${tagCount.tag} 推薦失敗", it) }.getOrDefault(emptyList())
            val ownSongs = own.map { MixSong(it.id, it.title, it.artistsText, it.durationText, it.thumbnailUrl, own = true) }
            DailyMix(
                tag = tagCount.tag,
                songs = interleave(ownSongs, recs),
                ownCount = ownSongs.size,
                matched = ownSongs.size + recs.size
            ).also { Log.i(TAG, "${it.tag}：自己 ${it.ownCount} 首＋推薦 ${recs.size} 首") }
        }.filter { it.songs.isNotEmpty() }
    }

    private suspend fun recommend(
        seeds: List<Song>,
        tagId: String,
        index: YtTagIndex,
        exclude: Set<String>
    ): List<MixSong> = seeds
        .flatMap { seed ->
            Innertube.nextPage(NextBody(videoId = seed.id))?.getOrNull()?.itemsPage?.items.orEmpty()
        }
        .filter { item ->
            val id = item.info?.endpoint?.videoId ?: return@filter false
            id !in exclude && tagId in index.tagsOf(item)
        }
        .distinctBy { it.info?.endpoint?.videoId }
        .take(RECS_PER_MIX)
        .map { item ->
            MixSong(
                id = item.info!!.endpoint!!.videoId!!,
                title = item.info?.name.orEmpty(),
                artistsText = item.authors?.joinToString(", ") { it.name.orEmpty() }?.takeIf { it.isNotBlank() },
                durationText = item.durationText,
                thumbnailUrl = item.thumbnail?.url
            )
        }

    /** 自己的歌和推薦交錯排：預設每 3 首插 2 首推薦，不會整段都是新歌。 */
    internal fun <T> interleave(own: List<T>, recs: List<T>, ownRun: Int = 3, recRun: Int = 2): List<T> {
        val result = ArrayList<T>(own.size + recs.size)
        var o = 0
        var r = 0
        while (o < own.size || r < recs.size) {
            repeat(ownRun) { if (o < own.size) result += own[o++] }
            repeat(recRun) { if (r < recs.size) result += recs[r++] }
        }
        return result
    }
}
