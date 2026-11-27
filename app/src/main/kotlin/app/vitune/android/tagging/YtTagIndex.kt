package app.vitune.android.tagging

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.BrowseBody
import app.vitune.providers.innertube.requests.browse
import app.vitune.providers.innertube.requests.playlistPage
import app.vitune.providers.tags.TagNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.days
import app.vitune.providers.innertube.models.Context as YtContext

private const val TAG = "YtTagIndex"

/**
 * YouTube Music 官方「心情與曲風」反查索引：
 * 抓每個分類前幾個官方歌單，記下哪些歌、哪些歌手出現在哪個分類。
 * 結果存在 filesDir/yt_tag_index.json，7 天更新一次；網路失敗時沿用舊的。
 */
@Serializable
data class YtTagIndex(
    val builtAt: Long,
    /** videoId → tag id */
    val songs: Map<String, List<String>>,
    /** 歌手 channel id（UC…）→ tag id → 出現次數 */
    val artists: Map<String, Map<String, Int>>,
    /** 歌手名稱（小寫）→ tag id → 出現次數；資料庫沒有歌手 id 時用 */
    val artistNames: Map<String, Map<String, Int>>
) {
    /** 一首 YouTube 歌（電台、推薦）的分類：官方歌單直接收錄的＋依歌手推估的（規則同 SongTagger）。 */
    fun tagsOf(item: Innertube.SongItem): Set<String> {
        val id = item.info?.endpoint?.videoId ?: return emptySet()
        val direct = songs[id].orEmpty()
        val authors = item.authors.orEmpty()
        val counts = authors.mapNotNull { a -> a.endpoint?.browseId?.let { artists[it] } }
            .ifEmpty { authors.mapNotNull { a -> a.name?.trim()?.lowercase()?.let { artistNames[it] } } }
        return (direct + SongTagger.fromArtistCounts(counts).map { it.tag.tag.id }).toSet()
    }

    companion object {
        private const val FILE = "yt_tag_index.json"
        private const val PLAYLISTS_PER_CATEGORY = 6
        private const val PARALLEL_REQUESTS = 4
        private val TTL = 7.days
        private val json = Json { ignoreUnknownKeys = true }

        /** 背景分類和首頁 Daily Mix 可能同時要索引：一次只讓一個人建，其他人等它建完直接讀快取。 */
        private val loadMutex = Mutex()

        suspend fun load(context: Context, forceRefresh: Boolean = false): YtTagIndex? =
            withContext(Dispatchers.IO) {
                val requestedAt = System.currentTimeMillis()
                loadMutex.withLock {
                    val file = AtomicFile(File(context.filesDir, FILE))
                    val cached = catching { json.decodeFromString(serializer(), file.readFully().decodeToString()) }.getOrNull()
                    val fresh = cached != null &&
                        System.currentTimeMillis() - cached.builtAt < TTL.inWholeMilliseconds
                    // 強制更新時，等鎖期間別人剛建好的也算數，不重建第二次
                    val builtWhileWaiting = cached != null && cached.builtAt >= requestedAt
                    if (fresh && (!forceRefresh || builtWhileWaiting)) return@withLock cached

                    val built = catching { build() }
                        .onFailure { Log.w(TAG, "建立索引失敗", it) }
                        .getOrNull()
                    // 強制重建失敗：回 null，讓呼叫端知道「沒有新索引」，不要拿舊索引假裝重做完了
                    if (built == null) return@withLock if (forceRefresh) null else cached

                    // 寫到一半被中斷也不會留下壞掉的快取
                    catching { writeAtomically(file, json.encodeToString(serializer(), built)) }
                        .onFailure { Log.w(TAG, "索引存檔失敗", it) }
                    Log.i(TAG, "索引完成：${built.songs.size} 首歌、${built.artists.size} 位歌手")
                    built
                }
            }

        /** 分類名稱固定用英文（hl=en）才能穩定對應；地區（gl）跟手機一致，才會有華語等在地分類。 */
        internal fun ytContext(): YtContext {
            val base = YtContext.DefaultWebNoLang
            val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
            return base.copy(client = base.client.copy(hl = "en", gl = country))
        }

        private suspend fun build(): YtTagIndex? = coroutineScope {
            val yt = ytContext()
            val root = Innertube.browse(BrowseBody(context = yt, browseId = "FEmusic_moods_and_genres"))
                ?.getOrNull() ?: return@coroutineScope null

            val categories = root.items
                .flatMap { it.items }
                .filterIsInstance<Innertube.Mood.Item>()
                .mapNotNull { item -> TagNormalizer.normalize(item.title)?.let { it to item.endpoint } }
            Log.i(TAG, "官方分類 ${categories.size} 個可對應")
            if (categories.isEmpty()) return@coroutineScope null

            val songs = HashMap<String, MutableSet<String>>()
            val artists = HashMap<String, HashMap<String, Int>>()
            val artistNames = HashMap<String, HashMap<String, Int>>()
            val mutex = Mutex()
            val semaphore = Semaphore(PARALLEL_REQUESTS)
            val requests = AtomicInteger()
            val failures = AtomicInteger()

            fun HashMap<String, HashMap<String, Int>>.bump(key: String, tagId: String) {
                val counts = getOrPut(key) { HashMap() }
                counts[tagId] = (counts[tagId] ?: 0) + 1
            }

            categories.map { (tag, endpoint) ->
                async {
                    val browseId = endpoint.browseId ?: return@async
                    requests.incrementAndGet()
                    val page = semaphore.withPermit {
                        Innertube.browse(BrowseBody(context = yt, browseId = browseId, params = endpoint.params))
                            ?.getOrNull()
                    } ?: run {
                        failures.incrementAndGet()
                        return@async
                    }

                    page.items
                        .flatMap { it.items }
                        .filterIsInstance<Innertube.PlaylistItem>()
                        .take(PLAYLISTS_PER_CATEGORY)
                        .forEach { playlist ->
                            val playlistId = playlist.info?.endpoint?.browseId ?: return@forEach
                            requests.incrementAndGet()
                            val items = semaphore.withPermit {
                                Innertube.playlistPage(BrowseBody(context = yt, browseId = playlistId))
                                    ?.getOrNull()?.songsPage?.items
                            } ?: run {
                                failures.incrementAndGet()
                                emptyList()
                            }

                            mutex.withLock {
                                items.forEach { song ->
                                    val videoId = song.info?.endpoint?.videoId ?: return@forEach
                                    songs.getOrPut(videoId) { mutableSetOf() } += tag.id
                                    song.authors.orEmpty().forEach { author ->
                                        author.endpoint?.browseId?.let { artists.bump(it, tag.id) }
                                        author.name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                                            ?.let { artistNames.bump(it, tag.id) }
                                    }
                                }
                            }
                        }
                }
            }.awaitAll()

            // 網路中途斷掉、很多請求失敗：還是先用，但標成「快過期」，明天就重建，不要殘缺一整週
            val partial = failures.get() * 10 > requests.get() * 3
            if (partial) Log.w(TAG, "索引不完整：${failures.get()}/${requests.get()} 個請求失敗，明天重建")
            val now = System.currentTimeMillis()
            if (songs.isEmpty()) null
            else YtTagIndex(
                builtAt = if (partial) now - (TTL - 1.days).inWholeMilliseconds else now,
                songs = songs.mapValues { it.value.toList() },
                artists = artists,
                artistNames = artistNames
            )
        }
    }
}
