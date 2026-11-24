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
    val thumbnailUrl: String? = null
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

    suspend fun load(context: Context, force: Boolean = false): List<DailyMix> = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toEpochDay()
        val file = File(context.filesDir, FILE)
        val cached = runCatching { json.decodeFromString(DailyMixCache.serializer(), file.readText()) }.getOrNull()
        if (!force && cached != null && cached.day == today && cached.mixes.isNotEmpty()) return@withContext cached.mixes

        val built = runCatching { build(context, today) }
            .onFailure { Log.w(TAG, "產生 Daily Mix 失敗", it) }
            .getOrNull()
            .orEmpty()
        if (built.isEmpty()) return@withContext cached?.mixes.orEmpty()

        runCatching { file.writeText(json.encodeToString(DailyMixCache.serializer(), DailyMixCache(today, built))) }
        built
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

        return chosen.map { tagCount ->
            val random = Random(day * 31 + tagCount.tag.hashCode())
            val pool = Database.songsWithTag(tagCount.tag, OWN_POOL * 2)
            // 前 25 首最常聽的裡面，每天隨機挑 15 首
            val own = pool.take(OWN_POOL).shuffled(random).take(OWN_PER_MIX)
            val recs = if (index == null) emptyList()
            else recommend(
                seeds = own.shuffled(random).take(SEEDS_PER_MIX),
                tagId = tagCount.tag,
                index = index,
                exclude = pool.map { it.id }.toSet()
            )
            val ownSongs = own.map { MixSong(it.id, it.title, it.artistsText, it.durationText, it.thumbnailUrl) }
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
            id !in exclude && tagId in tagsOf(index, item)
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

    /** 推薦歌的分類：官方歌單直接收錄的＋依歌手推估的（規則同 SongTagger）。 */
    private fun tagsOf(index: YtTagIndex, item: Innertube.SongItem): Set<String> {
        val id = item.info?.endpoint?.videoId ?: return emptySet()
        val direct = index.songs[id].orEmpty()
        val authors = item.authors.orEmpty()
        val counts = authors.mapNotNull { a -> a.endpoint?.browseId?.let { index.artists[it] } }
            .ifEmpty { authors.mapNotNull { a -> a.name?.trim()?.lowercase()?.let { index.artistNames[it] } } }
        return (direct + SongTagger.fromArtistCounts(counts).map { it.tag.tag.id }).toSet()
    }

    /** 自己的歌和推薦交錯排，大約每 3 首插 2 首推薦，不會整段都是新歌。 */
    internal fun <T> interleave(own: List<T>, recs: List<T>): List<T> {
        val result = ArrayList<T>(own.size + recs.size)
        var o = 0
        var r = 0
        while (o < own.size || r < recs.size) {
            repeat(3) { if (o < own.size) result += own[o++] }
            repeat(2) { if (r < recs.size) result += recs[r++] }
        }
        return result
    }
}
