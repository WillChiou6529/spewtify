package app.vitune.android.ui.screens.builtinplaylist

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import app.vitune.android.utils.semiBold
import app.vitune.android.ui.components.themed.SpotifyPlayRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.Database
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.Song
import app.vitune.android.preferences.DataPreferences
import app.vitune.android.ui.components.LocalMenuState
import app.vitune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.InHistoryMediaItemMenu
import app.vitune.android.ui.components.themed.NonQueuedMediaItemMenu
import app.vitune.android.tagging.tagDisplayName
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.ui.components.themed.TagChipRow
import app.vitune.android.ui.components.themed.ValueSelectorDialog
import app.vitune.android.ui.items.SongItem
import app.vitune.android.ui.screens.home.HeaderSongSortBy
import app.vitune.android.utils.PlaylistDownloadIcon
import app.vitune.android.utils.asMediaItem
import app.vitune.android.utils.enqueue
import app.vitune.android.utils.forcePlayAtIndex
import app.vitune.android.utils.forcePlayFromBeginning
import app.vitune.android.utils.playingSong
import app.vitune.compose.persist.persistList
import app.vitune.core.data.enums.BuiltInPlaylist
import app.vitune.core.data.enums.SongSortBy
import app.vitune.core.data.enums.SortOrder
import app.vitune.core.ui.Dimensions
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.enumSaver
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun BuiltInPlaylistSongs(
    builtInPlaylist: BuiltInPlaylist,
    modifier: Modifier = Modifier
) = with(DataPreferences) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    var songs by persistList<Song>("${builtInPlaylist.name}/songs")

    var sortBy by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SongSortBy.DateAdded) }
    var sortOrder by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SortOrder.Descending) }

    LaunchedEffect(binder, sortBy, sortOrder) {
        when (builtInPlaylist) {
            BuiltInPlaylist.Favorites -> Database.favorites(
                sortBy = sortBy,
                sortOrder = sortOrder
            )

            BuiltInPlaylist.Offline ->
                Database
                    .songsWithContentLength(
                        sortBy = sortBy,
                        sortOrder = sortOrder
                    )
                    .map { songs ->
                        songs.filter { binder?.isCached(it) ?: false }.map { it.song }
                    }

            BuiltInPlaylist.Top -> combine(
                flow = topListPeriodProperty.stateFlow,
                flow2 = topListLengthProperty.stateFlow
            ) { period, length -> period to length }.flatMapLatest { [period, length] ->
                if (period.duration == null) Database
                    .songsByPlayTimeDesc(limit = length)
                    .distinctUntilChanged()
                    .cancellable()
                else Database
                    .trending(
                        limit = length,
                        period = period.duration.inWholeMilliseconds
                    )
                    .distinctUntilChanged()
                    .cancellable()
            }

            BuiltInPlaylist.History -> Database.history()
        }.collect { songs = it.toImmutableList() }
    }

    // Spewtify：收藏依曲風／心情篩選（只在收藏頁）
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    val tagCounts by remember { Database.favoriteTagCounts().distinctUntilChanged() }
        .collectAsState(initial = emptyList())
    val taggedIds by remember(selectedTag) {
        selectedTag?.let { tag -> Database.songIdsWithTag(tag).map<List<String>, Set<String>> { it.toHashSet() } }
            ?: flowOf<Set<String>>(emptySet())
    }.collectAsState(initial = emptySet<String>())
    // 選中的標籤沒有歌了（例如全部重新分類、取消收藏）→ 自動回到「全部」，不留一個空列表
    LaunchedEffect(tagCounts, selectedTag) {
        val tag = selectedTag ?: return@LaunchedEffect
        if (tagCounts.isNotEmpty() && tagCounts.none { it.tag == tag }) selectedTag = null
    }
    val shown = if (builtInPlaylist != BuiltInPlaylist.Favorites || selectedTag == null) songs
    else songs.filter { it.id in taggedIds }

    val lazyListState = rememberLazyListState()

    val [currentMediaId, playing] = playingSong(binder)

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
        ) {
            // Spewtify：「已按讚的歌曲」Spotify 紫藍漸層標頭（大標題＋首數）
            if (builtInPlaylist == BuiltInPlaylist.Favorites) item(key = "liked_banner") {
                Box(
                    contentAlignment = Alignment.BottomStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF5038A0), Color(0xFF2A1F5C), colorPalette.background0)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        BasicText(
                            text = stringResource(R.string.liked_songs),
                            style = typography.xxl.semiBold.copy(fontSize = 30.sp, color = Color.White)
                        )
                        BasicText(
                            text = stringResource(R.string.format_library_song_count, shown.size),
                            style = typography.xs.copy(color = Color.White.copy(alpha = 0.75f)),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item(key = "spotify_play") {
                SpotifyPlayRow(
                    enabled = shown.isNotEmpty(),
                    onShuffle = {
                        binder?.stopRadio()
                        binder?.player?.forcePlayFromBeginning(shown.shuffled().map(Song::asMediaItem))
                    },
                    onPlay = {
                        binder?.stopRadio()
                        binder?.player?.forcePlayFromBeginning(shown.map(Song::asMediaItem))
                    }
                )
            }

            item(
                key = "header",
                contentType = 0
            ) {
                Header(
                    title = when (builtInPlaylist) {
                        // 已按讚的歌曲標題在上面的漸層標頭；這裡只顯示目前篩選的曲風
                        BuiltInPlaylist.Favorites -> selectedTag?.let { tagDisplayName(it) }.orEmpty()

                        BuiltInPlaylist.Offline -> stringResource(R.string.offline)

                        BuiltInPlaylist.Top -> stringResource(
                            R.string.format_my_top_playlist,
                            topListLength
                        )

                        BuiltInPlaylist.History -> stringResource(R.string.history)
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    SecondaryTextButton(
                        text = stringResource(R.string.enqueue),
                        enabled = shown.isNotEmpty(),
                        onClick = {
                            binder?.player?.enqueue(shown.map(Song::asMediaItem))
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (builtInPlaylist != BuiltInPlaylist.Offline) PlaylistDownloadIcon(
                        songs = shown.map(Song::asMediaItem).toImmutableList()
                    )

                    if (builtInPlaylist.sortable) HeaderSongSortBy(
                        sortBy = sortBy,
                        setSortBy = { sortBy = it },
                        sortOrder = sortOrder,
                        setSortOrder = { sortOrder = it }
                    )

                    if (builtInPlaylist == BuiltInPlaylist.Top) {
                        var dialogShowing by rememberSaveable { mutableStateOf(false) }

                        SecondaryTextButton(
                            text = topListPeriod.displayName(),
                            onClick = { dialogShowing = true }
                        )

                        if (dialogShowing) ValueSelectorDialog(
                            onDismiss = { dialogShowing = false },
                            title = stringResource(
                                R.string.format_view_top_of_header,
                                topListLength
                            ),
                            selectedValue = topListPeriod,
                            values = DataPreferences.TopListPeriod.entries.toImmutableList(),
                            onValueSelect = { topListPeriod = it },
                            valueText = { it.displayName() }
                        )
                    }
                }
            }

            if (builtInPlaylist == BuiltInPlaylist.Favorites && tagCounts.isNotEmpty()) item(
                key = "tags",
                contentType = 1
            ) {
                TagChipRow(
                    tags = tagCounts,
                    selected = selectedTag,
                    onSelect = { selectedTag = it },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            itemsIndexed(
                items = shown,
                key = { _, song -> song.id },
                contentType = { _, song -> song }
            ) { index, song ->
                SongItem(
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                menuState.display {
                                    when (builtInPlaylist) {
                                        BuiltInPlaylist.Offline -> InHistoryMediaItemMenu(
                                            song = song,
                                            onDismiss = menuState::hide
                                        )

                                        BuiltInPlaylist.Favorites,
                                        BuiltInPlaylist.Top,
                                        BuiltInPlaylist.History -> NonQueuedMediaItemMenu(
                                            mediaItem = song.asMediaItem,
                                            onDismiss = menuState::hide
                                        )
                                    }
                                }
                            },
                            onClick = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    items = shown.map(Song::asMediaItem),
                                    index = index
                                )
                            }
                        )
                        .animateItem(),
                    song = song,
                    index = if (builtInPlaylist == BuiltInPlaylist.Top) index else null,
                    thumbnailSize = Dimensions.thumbnails.song,
                    isPlaying = playing && currentMediaId == song.id
                )
            }
        }

        // Spotify 沒有浮動按鈕，只保留回到頂端
        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}
