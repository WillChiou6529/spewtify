package app.vitune.android.charts

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import app.vitune.android.tagging.catching
import app.vitune.android.tagging.writeAtomically
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.BrowseBody
import app.vitune.providers.innertube.requests.browse
import app.vitune.providers.innertube.requests.playlistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.days

private const val TAG = "GenreCovers"

/**
 * 搜尋頁「瀏覽全部」每一格的封面：該曲風分類裡第一個歌單的第一首歌的封面（沒有歌就用歌單封面）。
 * 每格要 2 個請求，所以存檔快取 7 天；同時最多 3 個請求，不搶播放的網路。
 */
object GenreCovers {
    private const val FILE = "genre_covers.json"
    private val TTL = 7.days
    private val json = Json { ignoreUnknownKeys = true }
    private val semaphore = Semaphore(3)
    private val diskMutex = Mutex()

    /** 分類 key → 封面網址（"" = 這個分類找不到封面） */
    private val memory = ConcurrentHashMap<String, String>()
    private var diskLoaded = false
    /** 這批封面第一次存檔的時間；過了 7 天整批重抓（分類內容會變） */
    private var savedAt = 0L

    @Serializable
    private data class Store(val savedAt: Long, val covers: Map<String, String>)

    /** 不連網路，只看記憶體（畫面重建時先拿來顯示）。 */
    fun peek(item: Innertube.Mood.Item): String? = memory[item.key]?.ifEmpty { null }

    suspend fun cover(context: Context, item: Innertube.Mood.Item): String? = withContext(Dispatchers.IO) {
        loadDisk(context)
        memory[item.key]?.let { return@withContext it.ifEmpty { null } }
        // 網路失敗回 null 不記，下次再試；確定沒有封面才記 ""
        val url = semaphore.withPermit { fetch(item) } ?: return@withContext null
        memory[item.key] = url
        saveDisk(context)
        url.ifEmpty { null }
    }

    /** 成功回網址或 ""（確定沒有）；網路失敗回 null。 */
    private suspend fun fetch(item: Innertube.Mood.Item): String? {
        val browseId = item.endpoint.browseId ?: return ""
        val page = Innertube.browse(BrowseBody(browseId = browseId, params = item.endpoint.params))
            ?.onFailure { Log.w(TAG, "分類 ${item.title} 失敗", it) }
            ?.getOrNull() ?: return null
        val playlist = page.items
            .flatMap { it.items }
            .filterIsInstance<Innertube.PlaylistItem>()
            .firstOrNull() ?: return ""
        val playlistId = playlist.info?.endpoint?.browseId ?: return playlist.thumbnail?.url.orEmpty()
        val firstSong = Innertube.playlistPage(BrowseBody(browseId = playlistId))
            ?.getOrNull()?.songsPage?.items?.firstOrNull()
        return firstSong?.thumbnail?.url ?: playlist.thumbnail?.url.orEmpty()
    }

    private suspend fun loadDisk(context: Context) = diskMutex.withLock {
        if (diskLoaded) return@withLock
        diskLoaded = true
        val store = catching {
            json.decodeFromString(Store.serializer(), AtomicFile(File(context.filesDir, FILE)).readFully().decodeToString())
        }.getOrNull() ?: return@withLock
        if (System.currentTimeMillis() - store.savedAt < TTL.inWholeMilliseconds) {
            savedAt = store.savedAt
            store.covers.forEach { (k, v) -> memory.putIfAbsent(k, v) }
        }
    }

    private suspend fun saveDisk(context: Context) = diskMutex.withLock {
        if (savedAt == 0L) savedAt = System.currentTimeMillis()
        catching {
            writeAtomically(
                AtomicFile(File(context.filesDir, FILE)),
                json.encodeToString(Store.serializer(), Store(savedAt, HashMap(memory)))
            )
        }.onFailure { Log.w(TAG, "存檔失敗", it) }
    }
}
