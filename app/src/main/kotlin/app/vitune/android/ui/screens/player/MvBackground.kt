package app.vitune.android.ui.screens.player

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Matrix
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import android.view.TextureView
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.vitune.android.Dependencies
import app.vitune.android.service.LOCAL_KEY_PREFIX
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.tagging.catching
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.requests.officialMusicVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "MvBackground"

/** MV 影片的磁碟快取（50MB，最久沒看的先刪）；跟播歌的快取分開，不會擠掉歌曲快取。 */
@OptIn(UnstableApi::class)
private object MvCache {
    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "mv_cache"),
            LeastRecentlyUsedCacheEvictor(50L * 1024 * 1024),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { cache = it }
    }
}

/** 歌曲 id → MV 影像網址（"" = 這首沒有 MV）。App 關掉前有效；網址大約幾小時後過期，失敗會重抓。 */
private val mvUrlCache = ConcurrentHashMap<String, String>()

/** 目前是不是不計費網路（Wi-Fi）；網路切換時會跟著變（Wi-Fi 換成行動網路，MV 立刻停）。 */
@Composable
private fun rememberIsUnmetered(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService<ConnectivityManager>() }
    var unmetered by remember { mutableStateOf(cm?.let { !it.isActiveNetworkMetered } ?: false) }
    DisposableEffect(cm) {
        if (cm == null) return@DisposableEffect onDispose { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }

            override fun onLost(network: Network) {
                unmetered = false
            }
        }
        val registered = runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose { if (registered) runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return unmetered
}

/** App 在前景（看得到畫面）；按 Home、關螢幕就變 false，MV 播放器跟著釋放，不在背景耗電耗流量。 */
@Composable
private fun rememberIsForeground(): Boolean {
    val context = LocalContext.current
    val owner = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<LifecycleOwner>().firstOrNull()
    } ?: return true
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return foreground
}

private suspend fun resolveMvUrl(mediaId: String, title: String, artist: String?): String? = withContext(Dispatchers.IO) {
    mvUrlCache[mediaId]?.let { return@withContext it.ifEmpty { null } }
    // 連按下一首時，前幾首還沒找完就被取消，不會一直排隊跑 yt-dlp、跟真正要播的歌搶資源
    delay(700)
    // 網路失敗（沒網路、逾時）不記住，下次展開再找；只有「確定沒有 MV」才記成 ""
    val result = Innertube.officialMusicVideo(title, artist, mediaId) ?: return@withContext null
    val mvId = result
        .onFailure { Log.w(TAG, "找 MV 失敗：$title", it) }
        .getOrElse { return@withContext null }
        ?: run {
            Log.i(TAG, "$title → 沒有 MV")
            mvUrlCache[mediaId] = ""
            return@withContext null
        }
    val url = catching { Dependencies.runVideoUrl(mvId) }
        .onFailure { Log.w(TAG, "取 MV 網址失敗：$mvId", it) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?: return@withContext null
    Log.i(TAG, "$title → MV $mvId")
    mvUrlCache[mediaId] = url
    url
}

/**
 * 播放畫面背景的官方 MV 片段（靜音、循環、Spotify Canvas 那種滿版裁切）。
 * active＝播放畫面展開中才抓才播；沒有 MV、關閉設定、只限 Wi-Fi 但正在用行動網路時什麼都不畫。
 * onShowing(true)＝影片第一格已經出現，呼叫端可以把封面藏起來。
 */
@OptIn(UnstableApi::class)
@Composable
fun MvBackground(
    mediaItem: MediaItem?,
    isPlaying: Boolean,
    active: Boolean,
    audioPositionMs: () -> Long,
    onShowing: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaId = mediaItem?.mediaId
    val unmetered = rememberIsUnmetered()
    val foreground = rememberIsForeground()
    val enabled = active && foreground && mediaId != null && !mediaId.startsWith(LOCAL_KEY_PREFIX) &&
        PlayerPreferences.mvBackground && (!PlayerPreferences.mvBackgroundWifiOnly || unmetered)

    val url by produceState<String?>(initialValue = null, mediaId, enabled) {
        value = null
        onShowing(false)
        if (!enabled || mediaId == null) return@produceState
        val metadata = mediaItem?.mediaMetadata ?: return@produceState
        val title = metadata.title?.toString() ?: return@produceState
        value = resolveMvUrl(mediaId, title, metadata.artist?.toString())
    }

    val currentUrl = url ?: return

    val player = remember(currentUrl) {
        // 走磁碟快取：循環播放時第二圈起不用再下載，再展開同一首也不用重抓
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    CacheDataSource.Factory()
                        .setCache(MvCache.get(context))
                        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                )
            )
            .build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    val textureView = remember(currentUrl) { TextureView(context) }

    DisposableEffect(player) {
        var lastSize: VideoSize? = null
        // 畫面大小變了（第一次量好、旋轉）要重算裁切，不然影片會變形
        val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val resized = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
            if (resized) lastSize?.let { cropToFill(textureView, it) }
        }
        textureView.addOnLayoutChangeListener(layoutListener)
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = onShowing(true)

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                lastSize = videoSize
                cropToFill(textureView, videoSize)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w(TAG, "MV 播放失敗", error)
                mediaId?.let { mvUrlCache.remove(it) }   // 網址可能過期，下次重抓
                onShowing(false)
            }
        }
        player.addListener(listener)
        player.setVideoTextureView(textureView)
        // 網址每次取都不一樣（有時效參數），快取 key 用歌曲 id 才能重複用
        player.setMediaItem(MediaItem.Builder().setUri(currentUrl).setCustomCacheKey("mv:$mediaId").build())
        player.prepare()
        // 從跟歌差不多的位置開始，像「這首歌的片段」
        player.seekTo(audioPositionMs().coerceAtLeast(0L))
        onDispose {
            textureView.removeOnLayoutChangeListener(layoutListener)
            player.removeListener(listener)
            player.release()
            onShowing(false)
        }
    }

    LaunchedEffect(player, isPlaying) { player.playWhenReady = isPlaying }

    AndroidView(
        factory = { textureView },
        modifier = modifier
    )
}

/** 讓影片像 CSS object-fit: cover 一樣填滿 TextureView（多的部分裁掉，不變形）。 */
private fun cropToFill(view: TextureView, size: VideoSize) {
    if (size.width == 0 || size.height == 0) return
    view.post {
        val vw = view.width.toFloat()
        val vh = view.height.toFloat()
        if (vw == 0f || vh == 0f) return@post
        val videoAspect = size.width * size.pixelWidthHeightRatio / size.height
        val viewAspect = vw / vh
        val (sx, sy) = if (videoAspect > viewAspect) (videoAspect / viewAspect) to 1f else 1f to (viewAspect / videoAspect)
        view.setTransform(Matrix().apply { setScale(sx, sy, vw / 2f, vh / 2f) })
    }
}
