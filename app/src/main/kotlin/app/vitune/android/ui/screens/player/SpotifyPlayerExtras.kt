package app.vitune.android.ui.screens.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import app.vitune.android.Database
import app.vitune.android.R
import app.vitune.android.models.Lyrics
import app.vitune.android.service.LOCAL_KEY_PREFIX
import app.vitune.android.transaction
import app.vitune.android.ui.components.themed.IconButton
import app.vitune.android.utils.bold
import app.vitune.core.ui.LocalAppearance
import app.vitune.providers.lrclib.LrcParser
import app.vitune.providers.lrclib.toLrcFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * Spewtify：Spotify 播放畫面控制鍵下面那一排——
 * 左邊「裝置」（系統的輸出切換：藍牙耳機／喇叭），右邊「分享」＋「待播清單」。
 */
@Composable
fun PlayerActionRow(
    mediaItem: MediaItem?,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val context = LocalContext.current
    val canShare = mediaItem != null && !mediaItem.mediaId.startsWith(LOCAL_KEY_PREFIX)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        IconButton(
            icon = R.drawable.devices,
            color = colorPalette.text,
            onClick = { openOutputSwitcher(context) },
            modifier = Modifier.size(22.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canShare) IconButton(
                icon = R.drawable.share_social,
                color = colorPalette.text,
                onClick = { mediaItem?.let { shareSong(context, it) } },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(28.dp))
            IconButton(
                icon = R.drawable.queue_list,
                color = colorPalette.text,
                onClick = onQueueClick,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun shareSong(context: Context, mediaItem: MediaItem) {
    val title = mediaItem.mediaMetadata.title?.toString().orEmpty()
    val artist = mediaItem.mediaMetadata.artist?.toString().orEmpty()
    val label = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" – ")
    val url = "https://music.youtube.com/watch?v=${mediaItem.mediaId}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, if (label.isBlank()) url else "$label\n$url")
    }
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * 開系統的「媒體輸出」切換（跟 Spotify 的裝置鈕一樣選藍牙耳機、喇叭）。
 * Android 14+ 用官方 API；12、13 叫 SystemUI 的對話框；10、11 開設定面板；都不行就開藍牙設定。
 */
private fun openOutputSwitcher(context: Context) {
    if (Build.VERSION.SDK_INT >= 34 &&
        runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)
    ) return

    if (Build.VERSION.SDK_INT >= 31) {
        val sent = runCatching {
            context.sendBroadcast(
                Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                    .setPackage("com.android.systemui")
                    .putExtra("package_name", context.packageName)
            )
        }.isSuccess
        if (sent) return
    }

    if (Build.VERSION.SDK_INT >= 29) try {
        context.startActivity(
            Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
        )
        return
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }

    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
}

private const val PREVIEW_LINES = 7

/**
 * Spewtify：Spotify 播放畫面往下滑的「歌詞預覽」卡。
 * 有同步歌詞就跟著唱到的地方走（唱過的白字、還沒唱的暗字），只有純文字就顯示前幾行；
 * 沒有歌詞整張卡不出現。點卡片開全螢幕歌詞。
 */
@Composable
fun LyricsPreviewCard(
    mediaItem: MediaItem,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val mediaId = mediaItem.mediaId
    val currentDurationProvider by rememberUpdatedState(durationProvider)

    var lyrics by remember(mediaId) { mutableStateOf<Lyrics?>(null) }

    LaunchedEffect(mediaId) {
        // 這首歌這次只抓一次：連不上時存 null（下次再抓），存檔觸發的更新不會又馬上重抓
        var attempted = false
        Database
            .lyrics(mediaId)
            .distinctUntilChanged()
            .collect { current ->
                lyrics = current
                // 兩種歌詞都抓過（確定找不到會存成空字串）就不再抓
                if (attempted || (current?.fixed != null && current.synced != null)) return@collect
                attempted = true

                var duration = currentDurationProvider()
                while (duration == C.TIME_UNSET) {
                    delay(100)
                    duration = currentDurationProvider()
                }

                val fetched = try {
                    withContext(Dispatchers.IO) {
                        fetchLyrics(
                            mediaId = mediaId,
                            current = current,
                            mediaMetadata = mediaItem.mediaMetadata,
                            durationMs = duration
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } ?: return@collect

                lyrics = fetched
                transaction {
                    runCatching {
                        Database.insert(mediaItem)
                        Database.upsert(fetched)
                    }
                }
            }
    }

    // 同步歌詞：(時間, 句子)，去掉空白的間奏行
    val synced = remember(lyrics?.synced) {
        lyrics?.synced?.takeIf { it.isNotBlank() }?.let { raw ->
            LrcParser.parse(raw)?.toLrcFile()?.let { file ->
                val offset = file.offset?.inWholeMilliseconds ?: 0L
                file.lines.entries
                    .filter { it.value.isNotBlank() }
                    .map { (it.key - offset) to it.value.trim() }
            }
        }?.takeIf { it.isNotEmpty() }
    }
    val fixed = remember(lyrics?.fixed) {
        lyrics?.fixed?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
    }
    if (synced == null && fixed == null) return

    val startTime = lyrics?.startTime ?: 0L
    // 自己每 0.25 秒看一次進度，只有換到下一句才重畫（不跟著整個播放畫面每次進度更新都重組）
    val currentPositionProvider by rememberUpdatedState(positionProvider)
    val currentIndex by produceState(initialValue = -1, synced, startTime) {
        val lines = synced ?: return@produceState
        while (true) {
            val pos = currentPositionProvider() - startTime
            value = lines.indexOfLast { it.first <= pos }
            delay(250)
        }
    }

    val (lines, firstIndex) = if (synced != null) {
        val from = (currentIndex - 1).coerceIn(0, (synced.size - PREVIEW_LINES).coerceAtLeast(0))
        synced.drop(from).take(PREVIEW_LINES).map { it.second } to from
    } else fixed!!.take(PREVIEW_LINES) to 0

    val cardColor = lerp(colorPalette.accent, Color.Black, 0.35f)
    // 還沒唱到的句子：半透明白（主題色偏深時黑字會看不清）
    val upcoming = Color.White.copy(alpha = 0.5f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                text = stringResource(R.string.lyrics_preview),
                style = typography.s.bold.copy(color = Color.White),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                icon = R.drawable.expand,
                color = Color.White,
                onClick = onOpen,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        lines.forEachIndexed { i, line ->
            val sung = synced == null || firstIndex + i <= currentIndex
            BasicText(
                text = line,
                style = typography.l.bold.copy(
                    color = if (sung) Color.White else upcoming,
                    fontSize = 22.sp,
                    lineHeight = 30.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                text = stringResource(R.string.lyrics_show_more),
                style = typography.xs.bold.copy(color = Color.Black),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
