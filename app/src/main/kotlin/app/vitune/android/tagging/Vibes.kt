package app.vitune.android.tagging

import android.util.Log
import app.vitune.android.Database
import app.vitune.android.models.Song
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.BrowseBody
import app.vitune.providers.innertube.requests.browse
import app.vitune.providers.innertube.requests.playlistPage
import app.vitune.providers.tags.Situation
import app.vitune.providers.tags.Tag
import app.vitune.providers.tags.TagNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

private const val TAG = "Vibes"

enum class VibeKind { MOOD, SITUATION }

/**
 * 首頁「現在想聽什麼？」的選項：心情或情境。
 * official＝新歌從 YouTube Music 官方哪幾個「心情與曲風」分類抽；mood／situation＝自己收藏裡怎麼挑符合的歌。
 */
enum class Vibe(
    val id: String,
    val kind: VibeKind,
    val zhName: String,
    val enName: String,
    val official: List<Tag>,
    val mood: Tag?,
    val situation: Situation?,
    val colorFrom: Long,
    val colorTo: Long
) {
    HAPPY("happy", VibeKind.MOOD, "開心", "Happy", listOf(Tag.FEEL_GOOD), Tag.FEEL_GOOD, null, 0xFFF59F00, 0xFFE8590C),
    RELAX("relax", VibeKind.MOOD, "放鬆", "Relaxed", listOf(Tag.CHILL), Tag.CHILL, null, 0xFF0CA678, 0xFF1098AD),
    SAD("sad", VibeKind.MOOD, "難過", "Sad", listOf(Tag.SAD), Tag.SAD, null, 0xFF364FC7, 0xFF212529),
    ROMANCE("romance", VibeKind.MOOD, "浪漫", "Romantic", listOf(Tag.ROMANCE), Tag.ROMANCE, null, 0xFFD6336C, 0xFF862E9C),
    HYPE("hype", VibeKind.MOOD, "熱血", "Pumped", listOf(Tag.ENERGY, Tag.WORKOUT), Tag.ENERGY, null, 0xFFE03131, 0xFFF76707),

    STUDY("study", VibeKind.SITUATION, "讀書", "Study", listOf(Tag.FOCUS, Tag.CHILL), null, Situation.STUDY, 0xFF3B5BDB, 0xFF15AABF),
    RIDE("ride", VibeKind.SITUATION, "騎重機", "Riding", listOf(Tag.ENERGY, Tag.ROCK, Tag.COMMUTE), null, Situation.RIDE, 0xFF212529, 0xFFE8590C),
    DRIVE("drive", VibeKind.SITUATION, "開車", "Driving", listOf(Tag.COMMUTE, Tag.FEEL_GOOD), null, Situation.DRIVE, 0xFF2B8A3E, 0xFFFAB005),
    WORKOUT("workout", VibeKind.SITUATION, "健身", "Workout", listOf(Tag.WORKOUT, Tag.ENERGY), null, Situation.WORKOUT, 0xFFE8590C, 0xFFF03E3E),
    PARTY("party", VibeKind.SITUATION, "派對", "Party", listOf(Tag.PARTY), null, Situation.PARTY, 0xFFAE3EC9, 0xFFF06595),
    SLEEP("sleep", VibeKind.SITUATION, "睡前", "Bedtime", listOf(Tag.SLEEP, Tag.CHILL), null, Situation.SLEEP, 0xFF5F3DC4, 0xFF212529);

    fun displayName(): String = if (Locale.getDefault().language == "zh") zhName else enName
}

/** 產生結果：songs 已經排好（約每 1 首收藏接 2 首新歌）；fresh = 0 通常代表沒網路。 */
data class VibeResult(val songs: List<MixSong>, val ownCount: Int, val freshCount: Int)

object VibePlaylist {
    private const val TOTAL = 50

    /** 首頁篩選用：收藏裡符合這個心情／情境的歌（依原本順序，最多 limit 首）。 */
    suspend fun ownSongs(vibe: Vibe, limit: Int = 30): List<Song> = withContext(Dispatchers.IO) {
        val blocked = Database.blacklistedIds().toHashSet()
        when {
            vibe.situation != null -> Situations.songsFor(vibe.situation)
            vibe.mood != null -> Database.songsWithTag(vibe.mood.id, limit * 2)
            else -> emptyList()
        }.filter { it.id !in blocked }.take(limit)
    }

    /** 約 1/3 收藏裡符合的歌＋2/3 官方歌單的新歌；收藏不夠就用新歌補滿，沒網路就全用收藏。每次都重新洗牌。 */
    suspend fun build(vibe: Vibe, total: Int = TOTAL): VibeResult = withContext(Dispatchers.IO) {
        val random = Random(System.nanoTime())
        val blocked = Database.blacklistedIds().toHashSet()
        val liked = Database.likedSongsNow().mapTo(HashSet()) { it.id }

        val ownAll = when {
            vibe.situation != null -> Situations.songsFor(vibe.situation)
            vibe.mood != null -> Database.songsWithTag(vibe.mood.id, 300)
            else -> emptyList()
        }.filter { it.id !in blocked }

        val fresh = catching {
            OfficialPlaylists.songs(vibe.official, exclude = liked + blocked, limit = total, random = random)
        }.onFailure { Log.w(TAG, "抓官方歌單失敗：${vibe.id}", it) }.getOrDefault(emptyList())

        val ownTarget = (total + 2) / 3
        val ownCount = minOf(ownAll.size, maxOf(ownTarget, total - fresh.size))
        val own = ownAll.shuffled(random).take(ownCount)
            .map { MixSong(it.id, it.title, it.artistsText, it.durationText, it.thumbnailUrl, own = true) }
        val freshPick = fresh.take(total - own.size)

        Log.i(TAG, "${vibe.id}：收藏 ${own.size}（可選 ${ownAll.size}）＋新歌 ${freshPick.size}")
        VibeResult(
            songs = DailyMixes.interleave(own, freshPick, ownRun = 1, recRun = 2),
            ownCount = own.size,
            freshCount = freshPick.size
        )
    }
}

/** YouTube Music 官方「心情與曲風」分類 → 隨機抽幾個官方歌單的歌。分類清單在記憶體快取到 App 關掉。 */
object OfficialPlaylists {
    private const val PLAYLISTS_PER_REQUEST = 4
    private val mutex = Mutex()
    private var categories: Map<Tag, List<Pair<String, String?>>>? = null

    private suspend fun categories(): Map<Tag, List<Pair<String, String?>>> = mutex.withLock {
        categories?.let { return@withLock it }
        val root = Innertube.browse(BrowseBody(context = YtTagIndex.ytContext(), browseId = "FEmusic_moods_and_genres"))
            ?.getOrNull()
        val map = root?.items.orEmpty()
            .flatMap { it.items }
            .filterIsInstance<Innertube.Mood.Item>()
            .mapNotNull { item ->
                val tag = TagNormalizer.normalize(item.title) ?: return@mapNotNull null
                val browseId = item.endpoint.browseId ?: return@mapNotNull null
                tag to (browseId to item.endpoint.params)
            }
            .groupBy({ it.first }, { it.second })
        if (map.isNotEmpty()) categories = map
        map
    }

    /** 首頁篩選用：官方對應分類裡的歌單（有封面，點了開歌單頁）；網路失敗回空清單。 */
    suspend fun playlists(tags: List<Tag>, limit: Int = 12): List<Innertube.PlaylistItem> = coroutineScope {
        val yt = YtTagIndex.ytContext()
        val cats = catching { categories() }.getOrDefault(emptyMap())
        tags.flatMap { cats[it].orEmpty() }
            .map { (browseId, params) ->
                async {
                    catching {
                        Innertube.browse(BrowseBody(context = yt, browseId = browseId, params = params))
                            ?.getOrNull()?.items.orEmpty()
                            .flatMap { it.items }
                            .filterIsInstance<Innertube.PlaylistItem>()
                    }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { it.key }
            .take(limit)
    }

    suspend fun songs(tags: List<Tag>, exclude: Set<String>, limit: Int, random: Random): List<MixSong> =
        coroutineScope {
            val yt = YtTagIndex.ytContext()
            val cats = categories()
            val endpoints = tags.flatMap { cats[it].orEmpty() }
            if (endpoints.isEmpty()) return@coroutineScope emptyList()

            val playlistIds = endpoints.map { (browseId, params) ->
                async {
                    catching {
                        Innertube.browse(BrowseBody(context = yt, browseId = browseId, params = params))
                            ?.getOrNull()?.items.orEmpty()
                            .flatMap { it.items }
                            .filterIsInstance<Innertube.PlaylistItem>()
                            .mapNotNull { it.info?.endpoint?.browseId }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinct().shuffled(random).take(PLAYLISTS_PER_REQUEST)

            playlistIds.map { id ->
                async {
                    catching {
                        Innertube.playlistPage(BrowseBody(context = yt, browseId = id))
                            ?.getOrNull()?.songsPage?.items.orEmpty()
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
                .flatten()
                .mapNotNull { it.toMixSong() }
                .filter { it.id !in exclude }
                .distinctBy { it.id }
                .shuffled(random)
                .take(limit)
        }
}

internal fun Innertube.SongItem.toMixSong(): MixSong? {
    val id = info?.endpoint?.videoId ?: return null
    return MixSong(
        id = id,
        title = info?.name.orEmpty(),
        artistsText = authors?.joinToString(", ") { it.name.orEmpty() }?.takeIf { it.isNotBlank() },
        durationText = durationText,
        thumbnailUrl = thumbnail?.url
    )
}
