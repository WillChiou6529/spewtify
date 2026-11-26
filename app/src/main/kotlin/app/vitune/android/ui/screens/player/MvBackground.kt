package app.vitune.android.ui.screens.player

import android.content.Context
import android.graphics.Matrix
import android.net.ConnectivityManager
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.vitune.android.Dependencies
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.tagging.catching
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.requests.officialMusicVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "MvBackground"

/** 歌曲 id → MV 影像網址（"" = 這首沒有 MV）。App 關掉前有效；網址大約幾小時後過期，失敗會重抓。 */
private val mvUrlCache = ConcurrentHashMap<String, String>()

private fun Context.isUnmetered(): Boolean {
    val cm = getSystemService<ConnectivityManager>() ?: return false
    return !cm.isActiveNetworkMetered
}

private suspend fun resolveMvUrl(mediaId: String, title: String, artist: String?): String? = withContext(Dispatchers.IO) {
    mvUrlCache[mediaId]?.let { return@withContext it.ifEmpty { null } }
    val mvId = Innertube.officialMusicVideo(title, artist, mediaId)
        ?.onFailure { Log.w(TAG, "找 MV 失敗：$title", it) }
        ?.getOrNull()
    val url = mvId?.let { id ->
        catching { Dependencies.runVideoUrl(id) }.onFailure { Log.w(TAG, "取 MV 網址失敗：$id", it) }.getOrNull()
    }.orEmpty()
    Log.i(TAG, "$title → MV ${mvId ?: "無"}${if (url.isEmpty() && mvId != null) "（網址失敗）" else ""}")
    mvUrlCache[mediaId] = url
    url.ifEmpty { null }
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
    val enabled = active && mediaId != null && PlayerPreferences.mvBackground &&
        (!PlayerPreferences.mvBackgroundWifiOnly || context.isUnmetered())

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
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    val textureView = remember(currentUrl) { TextureView(context) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = onShowing(true)

            override fun onVideoSizeChanged(videoSize: VideoSize) = cropToFill(textureView, videoSize)

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w(TAG, "MV 播放失敗", error)
                mediaId?.let { mvUrlCache.remove(it) }   // 網址可能過期，下次重抓
                onShowing(false)
            }
        }
        player.addListener(listener)
        player.setVideoTextureView(textureView)
        player.setMediaItem(MediaItem.fromUri(currentUrl))
        player.prepare()
        // 從跟歌差不多的位置開始，像「這首歌的片段」
        player.seekTo(audioPositionMs().coerceAtLeast(0L))
        onDispose {
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
