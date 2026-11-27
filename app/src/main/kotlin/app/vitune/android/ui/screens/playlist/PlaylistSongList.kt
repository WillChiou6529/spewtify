package app.vitune.android.ui.screens.playlist

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.unit.dp
import app.vitune.android.utils.semiBold
import app.vitune.android.ui.components.themed.SpotifyPlayRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.vitune.android.Database
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.Playlist
import app.vitune.android.models.SongPlaylistMap
import app.vitune.android.query
import app.vitune.android.transaction
import app.vitune.android.ui.components.LocalMenuState
import app.vitune.android.ui.components.ShimmerHost
import app.vitune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.HeaderIconButton
import app.vitune.android.ui.components.themed.HeaderPlaceholder
import app.vitune.android.ui.components.themed.LayoutWithAdaptiveThumbnail
import app.vitune.android.ui.components.themed.NonQueuedMediaItemMenu
import app.vitune.android.ui.components.themed.PlaylistInfo
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.android.ui.components.themed.adaptiveThumbnailContent
import app.vitune.android.ui.items.SongItem
import app.vitune.android.ui.items.SongItemPlaceholder
import app.vitune.android.utils.PlaylistDownloadIcon
import app.vitune.android.utils.asMediaItem
import app.vitune.android.utils.completed
import app.vitune.android.utils.enqueue
import app.vitune.android.utils.forcePlayAtIndex
import app.vitune.android.utils.forcePlayFromBeginning
import app.vitune.android.utils.playingSong
import app.vitune.compose.persist.persist
import app.vitune.core.ui.Dimensions
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.isLandscape
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.BrowseBody
import app.vitune.providers.innertube.requests.playlistPage
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 排行榜標題：中文字用子字串比對，英文字用字界且不分大小寫（避免 "Stop"、"Desktop" 誤中 "top"）
private val chartTitleRegex = Regex(
    pattern = "百大|排行|熱播|發燒|前 50|\\b(?:top|charts?)\\b",
    option = RegexOption.IGNORE_CASE
)

@Composable
fun PlaylistSongList(
    browseId: String,
    params: String?,
    maxDepth: Int?,
    shouldDedup: Boolean,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    var playlistPage by persist<Innertube.PlaylistOrAlbumPage?>("playlist/$browseId/playlistPage")

    LaunchedEffect(Unit) {
        if (playlistPage != null && playlistPage?.songsPage?.continuation == null) return@LaunchedEffect

        playlistPage = withContext(Dispatchers.IO) {
            Innertube
                .playlistPage(BrowseBody(browseId = browseId, params = params))
                ?.completed(
                    maxDepth = maxDepth ?: Int.MAX_VALUE,
                    shouldDedup = shouldDedup
                )
                ?.getOrNull()
        }
    }

    var isImportingPlaylist by rememberSaveable { mutableStateOf(false) }

    if (isImportingPlaylist) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        initialTextInput = playlistPage?.title.orEmpty(),
        onDismiss = { isImportingPlaylist = false },
        onAccept = { text ->
            query {
                transaction {
                    val playlistId = Database.insert(
                        Playlist(
                            name = text,
                            browseId = browseId,
                            thumbnail = playlistPage?.thumbnail?.url
                        )
                    )

                    playlistPage?.songsPage?.items
                        ?.map(Innertube.SongItem::asMediaItem)
                        ?.onEach(Database::insert)
                        ?.mapIndexed { index, mediaItem ->
                            SongPlaylistMap(
                                songId = mediaItem.mediaId,
                                playlistId = playlistId,
                                position = index
                            )
                        }?.let(Database::insertSongPlaylistMaps)
                }
            }
        }
    )

    val headerContent: @Composable () -> Unit = {
        if (playlistPage == null) HeaderPlaceholder(modifier = Modifier.shimmer())
        else Header(title = playlistPage?.title ?: stringResource(R.string.unknown)) {
            SecondaryTextButton(
                text = stringResource(R.string.enqueue),
                enabled = playlistPage?.songsPage?.items?.isNotEmpty() == true,
                onClick = {
                    playlistPage?.songsPage?.items?.map(Innertube.SongItem::asMediaItem)
                        ?.let { mediaItems ->
                            binder?.player?.enqueue(mediaItems)
                        }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            playlistPage?.songsPage?.items?.map(Innertube.SongItem::asMediaItem)
                ?.let { PlaylistDownloadIcon(songs = it.toImmutableList()) }

            HeaderIconButton(
                icon = R.drawable.add,
                color = colorPalette.text,
                onClick = { isImportingPlaylist = true }
            )

            HeaderIconButton(
                icon = R.drawable.share_social,
                color = colorPalette.text,
                onClick = {
                    val url = playlistPage?.url
                        ?: "https://music.youtube.com/playlist?list=${browseId.removePrefix("VL")}"

                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }

                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            )
        }
    }

    val thumbnailContent = adaptiveThumbnailContent(
        isLoading = playlistPage == null,
        url = playlistPage?.thumbnail?.url
    )

    val lazyListState = rememberLazyListState()

    val [currentMediaId, playing] = playingSong(binder)

    LayoutWithAdaptiveThumbnail(
        thumbnailContent = thumbnailContent,
        modifier = modifier
    ) {
        Box {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette.background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0
                ) {
                    // Spewtify：Spotify 版面——大封面 → 標題與操作 → 資訊 → 隨機＋綠色播放鍵
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!isLandscape) thumbnailContent()
                        headerContent()
                        PlaylistInfo(playlist = playlistPage)
                        val items = playlistPage?.songsPage?.items.orEmpty()
                        SpotifyPlayRow(
                            enabled = items.isNotEmpty(),
                            onShuffle = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayFromBeginning(items.shuffled().map(Innertube.SongItem::asMediaItem))
                            },
                            onPlay = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayFromBeginning(items.map(Innertube.SongItem::asMediaItem))
                            }
                        )
                    }
                }

                // 排行榜（標題有 百大／排行／熱播／發燒／Top／Chart／前 50）前面顯示名次
                val isChart = chartTitleRegex.containsMatchIn(playlistPage?.title.orEmpty())

                itemsIndexed(items = playlistPage?.songsPage?.items ?: emptyList()) { index, song ->
                    val songModifier = Modifier.combinedClickable(
                        onLongClick = {
                            menuState.display {
                                NonQueuedMediaItemMenu(
                                    onDismiss = menuState::hide,
                                    mediaItem = song.asMediaItem
                                )
                            }
                        },
                        onClick = {
                            playlistPage?.songsPage?.items?.map(Innertube.SongItem::asMediaItem)
                                ?.let { mediaItems ->
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(mediaItems, index)
                                }
                        }
                    )

                    if (isChart) Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = "${index + 1}",
                            style = LocalAppearance.current.typography.s.semiBold.copy(
                                color = LocalAppearance.current.colorPalette.text,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .width(32.dp)
                        )
                        SongItem(
                            song = song,
                            thumbnailSize = Dimensions.thumbnails.song,
                            modifier = Modifier
                                .weight(1f)
                                .then(songModifier),
                            isPlaying = playing && currentMediaId == song.key
                        )
                    } else SongItem(
                        song = song,
                        thumbnailSize = Dimensions.thumbnails.song,
                        modifier = songModifier,
                        isPlaying = playing && currentMediaId == song.key
                    )
                }

                if (playlistPage == null) item(key = "loading") {
                    ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                        repeat(4) {
                            SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                        }
                    }
                }
            }

            // Spotify 沒有浮動按鈕，只保留回到頂端
            FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
        }
    }
}
