package app.vitune.android.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import app.vitune.android.tagging.Vibe
import app.vitune.android.tagging.VibePlaylist
import app.vitune.android.tagging.catching
import app.vitune.android.ui.components.themed.VibeRow
import app.vitune.android.ui.components.themed.ArtistCircleRow
import app.vitune.android.ui.components.themed.ChartCardRow
import app.vitune.android.ui.components.themed.BigSquare
import app.vitune.android.ui.components.themed.BigSquareRow
import app.vitune.android.ui.components.themed.RadioCardRow
import app.vitune.providers.innertube.models.bodies.BrowseBody
import app.vitune.providers.innertube.requests.artistPage
import app.vitune.android.utils.bold
import androidx.compose.ui.unit.sp
import app.vitune.android.charts.ChartCard
import app.vitune.android.charts.ChartRegion
import app.vitune.android.charts.Charts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import app.vitune.android.ui.components.themed.Shortcut
import app.vitune.android.ui.components.themed.ShortcutGrid
import app.vitune.android.tagging.tagDisplayName
import app.vitune.android.utils.toast
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import app.vitune.android.tagging.DailyMix
import app.vitune.android.tagging.DailyMixes
import app.vitune.android.ui.components.themed.DailyMixRow
import app.vitune.android.utils.forcePlayFromBeginning
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.Database
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.Song
import app.vitune.android.preferences.DataPreferences
import app.vitune.android.query
import app.vitune.android.ui.components.LocalMenuState
import app.vitune.android.ui.components.ShimmerHost
import app.vitune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.NonQueuedMediaItemMenu
import app.vitune.android.ui.components.themed.TextPlaceholder
import app.vitune.android.ui.items.AlbumItem
import app.vitune.android.ui.items.AlbumItemPlaceholder
import app.vitune.android.ui.items.ArtistItem
import app.vitune.android.ui.items.ArtistItemPlaceholder
import app.vitune.android.ui.items.PlaylistItem
import app.vitune.android.ui.items.PlaylistItemPlaceholder
import app.vitune.android.ui.items.SongItem
import app.vitune.android.ui.items.SongItemPlaceholder
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.asMediaItem
import app.vitune.android.utils.center
import app.vitune.android.utils.forcePlay
import app.vitune.android.utils.playingSong
import app.vitune.android.utils.rememberSnapLayoutInfo
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.compose.persist.persist
import app.vitune.core.ui.Dimensions
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.isLandscape
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.NavigationEndpoint
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.requests.relatedPage
import kotlinx.coroutines.flow.distinctUntilChanged

@Route
@Composable
fun QuickPicks(
    onAlbumClick: (Innertube.AlbumItem) -> Unit,
    onArtistClick: (Innertube.ArtistItem) -> Unit,
    onPlaylistClick: (Innertube.PlaylistItem) -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit = {},
    onLocalPlaylistClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val context = LocalContext.current

    // Spewtify：「現在想聽什麼？」正在產生歌單的那個選項（null＝沒有）
    var loadingVibe by remember { mutableStateOf<Vibe?>(null) }
    var loadingRadio by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Spewtify：每日 Mix（一天產生一次，存在 filesDir/daily_mix.json）
    val dailyMixes by produceState(initialValue = emptyList<DailyMix>()) {
        value = DailyMixes.load(context.applicationContext)
    }

    // Spewtify：排行榜（台灣、全球、日韓美）與台灣熱門藝人，記憶體快取 1 小時
    val chartCards by produceState(initialValue = emptyList<ChartCard>()) {
        value = catching { Charts.homeCards() }.getOrDefault(emptyList())
    }
    val chartArtists by produceState(initialValue = emptyList<Innertube.ArtistItem>()) {
        value = catching { Charts.page(ChartRegion.TW)?.artists.orEmpty().take(20) }.getOrDefault(emptyList())
    }

    // Spewtify：Spotify 式首頁捷徑（收藏、Daily Mix、自己的播放清單、最近聽過的歌）
    val localPlaylists by remember { Database.playlistPreviewsByDateAddedDesc().distinctUntilChanged() }
        .collectAsState(initial = emptyList())
    val recentSongs by remember { Database.recentSongs(8).distinctUntilChanged() }
        .collectAsState(initial = emptyList())
    val favoritesTitle = stringResource(R.string.liked_songs)
    val mixFormat = stringResource(R.string.format_daily_mix)
    val shortcuts = buildList {
        add(
            Shortcut(
                key = "favorites", title = favoritesTitle, thumbnailUrl = null,
                icon = R.drawable.heart, gradient = 0xFF4527A0 to 0xFF8FA6E8, onClick = onFavoritesClick
            )
        )
        dailyMixes.take(2).forEach { mix ->
            add(
                Shortcut(
                    key = "mix:${mix.tag}",
                    title = mixFormat.format(tagDisplayName(mix.tag)),
                    thumbnailUrl = mix.songs.firstOrNull()?.thumbnailUrl,
                    icon = R.drawable.sparkles,
                    onClick = {
                        binder?.stopRadio()
                        binder?.player?.forcePlayFromBeginning(mix.songs.map { it.toSong().asMediaItem })
                    }
                )
            )
        }
        localPlaylists.take(2).forEach { playlist ->
            add(
                Shortcut(
                    key = "playlist:${playlist.id}",
                    title = playlist.name,
                    thumbnailUrl = playlist.thumbnail,
                    icon = R.drawable.playlist,
                    onClick = { onLocalPlaylistClick(playlist.id) }
                )
            )
        }
        recentSongs.forEach { song ->
            if (size >= 8) return@forEach
            add(
                Shortcut(
                    key = "song:${song.id}",
                    title = song.title,
                    thumbnailUrl = song.thumbnailUrl,
                    icon = R.drawable.musical_notes,
                    onClick = {
                        val mediaItem = song.asMediaItem
                        binder?.stopRadio()
                        binder?.player?.forcePlay(mediaItem)
                        binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                    }
                )
            )
        }
    }

    var trending by persist<Song?>("home/trending")

    var relatedPageResult by persist<Result<Innertube.RelatedPage?>?>(tag = "home/relatedPageResult")

    LaunchedEffect(relatedPageResult, DataPreferences.shouldCacheQuickPicks) {
        if (DataPreferences.shouldCacheQuickPicks)
            relatedPageResult?.getOrNull()?.let { DataPreferences.cachedQuickPicks = it }
        else DataPreferences.cachedQuickPicks = Innertube.RelatedPage()
    }

    LaunchedEffect(DataPreferences.quickPicksSource) {
        if (
            DataPreferences.shouldCacheQuickPicks && !DataPreferences.cachedQuickPicks.let {
                it.albums.isNullOrEmpty() &&
                    it.artists.isNullOrEmpty() &&
                    it.playlists.isNullOrEmpty() &&
                    it.songs.isNullOrEmpty()
            }
        ) relatedPageResult = Result.success(DataPreferences.cachedQuickPicks)

        suspend fun handleSong(song: Song?) {
            if (relatedPageResult == null || trending?.id != song?.id) relatedPageResult =
                Innertube.relatedPage(
                    body = NextBody(videoId = (song?.id ?: "J7p4bzqLvCw"))
                )
            trending = song
        }

        when (DataPreferences.quickPicksSource) {
            DataPreferences.QuickPicksSource.Trending ->
                Database
                    .trending()
                    .distinctUntilChanged()
                    .collect { handleSong(it.firstOrNull()) }

            DataPreferences.QuickPicksSource.LastInteraction ->
                Database
                    .events()
                    .distinctUntilChanged()
                    .collect { handleSong(it.firstOrNull()?.song) }
        }
    }

    val scrollState = rememberScrollState()
    val quickPicksLazyGridState = rememberLazyGridState()

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 28.dp, bottom = 12.dp)
        .padding(endPaddingValues)
    // Spotify 區塊標題：大、粗
    val sectionTitleStyle = typography.l.bold.copy(fontSize = 22.sp, color = colorPalette.text)

    val [currentMediaId, playing] = playingSong(binder)

    BoxWithConstraints {
        val quickPicksLazyGridItemWidthFactor =
            if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.475f else 0.75f

        val snapLayoutInfoProvider = rememberSnapLayoutInfo(
            lazyGridState = quickPicksLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize * quickPicksLazyGridItemWidthFactor / 2f - itemSize / 2f)
            }
        )

        val itemInHorizontalGridWidth = maxWidth * quickPicksLazyGridItemWidthFactor

        Column(
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    windowInsets
                        .only(WindowInsetsSides.Vertical)
                        .asPaddingValues()
                )
        ) {
            // 頂部：設定（Spotify 頭像的位置）＋心情／情境標籤（Spotify「所有／音樂」標籤列的位置）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(endPaddingValues)
                    .padding(top = 12.dp, bottom = 16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colorPalette.background2)
                        .clickable(onClick = onSettingsClick)
                ) {
                    Image(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.text),
                        modifier = Modifier.size(20.dp)
                    )
                }
                VibeRow(
                loading = loadingVibe,
                modifier = Modifier.weight(1f),
                onClick = { vibe ->
                    loadingVibe = vibe
                    coroutineScope.launch {
                        val result = try {
                            catching { VibePlaylist.build(vibe) }.getOrNull()
                        } finally {
                            loadingVibe = null
                        }
                        if (result == null || result.songs.isEmpty()) {
                            context.toast(context.getString(R.string.vibe_empty))
                            return@launch
                        }
                        if (result.freshCount == 0) context.toast(context.getString(R.string.vibe_offline))
                        binder?.stopRadio()
                        binder?.player?.forcePlayFromBeginning(result.songs.map { it.toSong().asMediaItem })
                    }
                }
            )
            }

            ShortcutGrid(shortcuts = shortcuts, contentPadding = endPaddingValues)

            relatedPageResult?.getOrNull()?.let { related ->
                BasicText(
                    text = stringResource(R.string.home_start_listening_subtitle),
                    style = typography.xs.secondary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                        .padding(endPaddingValues)
                )
                BasicText(
                    text = stringResource(R.string.home_start_listening),
                    style = sectionTitleStyle,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 2.dp, bottom = 8.dp)
                        .padding(endPaddingValues)
                )
                LazyHorizontalGrid(
                    state = quickPicksLazyGridState,
                    rows = GridCells.Fixed(3),
                    flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                    contentPadding = endPaddingValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((Dimensions.thumbnails.song + Dimensions.items.verticalPadding * 2) * 3)
                ) {
                    trending?.let { song ->
                        item {
                            SongItem(
                                modifier = Modifier
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    onDismiss = menuState::hide,
                                                    mediaItem = song.asMediaItem,
                                                    onRemoveFromQuickPicks = {
                                                        query {
                                                            Database.clearEventsFor(song.id)
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        onClick = {
                                            val mediaItem = song.asMediaItem
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(mediaItem)
                                            binder?.setupRadio(
                                                NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                            )
                                        }
                                    )
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                    .width(itemInHorizontalGridWidth),
                                song = song,
                                thumbnailSize = Dimensions.thumbnails.song,
                                trailingContent = {
                                    Image(
                                        painter = painterResource(R.drawable.star),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette.accent),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                showDuration = false,
                                isPlaying = playing && currentMediaId == song.id
                            )
                        }
                    }

                    items(
                        items = related.songs?.dropLast(if (trending == null) 0 else 1)
                            ?: emptyList(),
                        key = Innertube.SongItem::key
                    ) { song ->
                        SongItem(
                            song = song,
                            thumbnailSize = Dimensions.thumbnails.song,
                            modifier = Modifier
                                .combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                onDismiss = menuState::hide,
                                                mediaItem = song.asMediaItem
                                            )
                                        }
                                    },
                                    onClick = {
                                        val mediaItem = song.asMediaItem
                                        binder?.stopRadio()
                                        binder?.player?.forcePlay(mediaItem)
                                        binder?.setupRadio(
                                            NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                        )
                                    }
                                )
                                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                .width(itemInHorizontalGridWidth),
                            showDuration = false,
                            isPlaying = playing && currentMediaId == song.key
                        )
                    }
                }

                // Spotify 順序：開始聆聽 → 熱門專輯和單曲（大方卡）→ 排行榜 → 熱門藝人 → 為你打造
                related.albums?.takeIf { it.isNotEmpty() }?.let { albums ->
                    BasicText(
                        text = stringResource(R.string.popular_albums_and_singles),
                        style = sectionTitleStyle,
                        modifier = sectionTextModifier
                    )
                    BigSquareRow(
                        items = albums.map {
                            BigSquare(
                                key = it.key,
                                thumbnailUrl = it.thumbnail?.url,
                                title = it.info?.name.orEmpty(),
                                subtitle = it.authors?.joinToString(", ") { a -> a.name.orEmpty() }
                            )
                        },
                        onClick = { index -> albums.getOrNull(index)?.let(onAlbumClick) },
                        contentPadding = endPaddingValues
                    )
                }

                if (chartCards.isNotEmpty()) {
                    BasicText(
                        text = stringResource(R.string.charts),
                        style = sectionTitleStyle,
                        modifier = sectionTextModifier
                    )
                    ChartCardRow(
                        cards = chartCards,
                        onClick = onPlaylistClick,
                        contentPadding = endPaddingValues
                    )
                }

                if (chartArtists.isNotEmpty()) {
                    BasicText(
                        text = stringResource(R.string.popular_artists),
                        style = sectionTitleStyle,
                        modifier = sectionTextModifier
                    )
                    ArtistCircleRow(
                        artists = chartArtists,
                        onClick = onArtistClick,
                        contentPadding = endPaddingValues
                    )
                }

                // 熱門電台：點了抓藝人頁的電台入口，直接開播
                if (chartArtists.isNotEmpty()) {
                    BasicText(
                        text = stringResource(R.string.popular_radio),
                        style = sectionTitleStyle,
                        modifier = sectionTextModifier
                    )
                    RadioCardRow(
                        artists = chartArtists.take(12),
                        loadingKey = loadingRadio,
                        onClick = { artist ->
                            loadingRadio = artist.key
                            coroutineScope.launch {
                                val endpoint = try {
                                    catching {
                                        Innertube.artistPage(BrowseBody(browseId = artist.key))?.getOrNull()?.radioEndpoint
                                    }.getOrNull()
                                } finally {
                                    loadingRadio = null
                                }
                                if (endpoint == null) {
                                    context.toast(context.getString(R.string.vibe_empty))
                                    return@launch
                                }
                                binder?.stopRadio()
                                binder?.playRadio(endpoint)
                            }
                        },
                        contentPadding = endPaddingValues
                    )
                }

                if (dailyMixes.isNotEmpty()) {
                    BasicText(
                        text = stringResource(R.string.daily_mixes),
                        style = sectionTitleStyle,
                        modifier = sectionTextModifier
                    )
                    DailyMixRow(
                        mixes = dailyMixes,
                        contentPadding = endPaddingValues,
                        onClick = { mix ->
                            binder?.stopRadio()
                            binder?.player?.forcePlayFromBeginning(mix.songs.map { it.toSong().asMediaItem })
                        }
                    )
                }

                related.artists?.let { artists ->
                    BasicText(
                        text = stringResource(R.string.similar_artists),
                        style = sectionTitleStyle,
                        modifier = sectionTextModifier
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = artists,
                            key = Innertube.ArtistItem::key
                        ) { artist ->
                            ArtistItem(
                                artist = artist,
                                thumbnailSize = Dimensions.thumbnails.artist,
                                alternative = true,
                                modifier = Modifier.clickable { onArtistClick(artist) }
                            )
                        }
                    }
                }

                related.playlists?.let { playlists ->
                    BasicText(
                        text = stringResource(R.string.recommended_playlists),
                        style = sectionTitleStyle,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 8.dp)
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = playlists,
                            key = Innertube.PlaylistItem::key
                        ) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSize = Dimensions.thumbnails.playlist,
                                alternative = true,
                                modifier = Modifier.clickable { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }

                Unit
            } ?: relatedPageResult?.exceptionOrNull()?.let {
                BasicText(
                    text = stringResource(R.string.error_message),
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(all = 16.dp)
                )
            } ?: ShimmerHost {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        AlbumItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        ArtistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        PlaylistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(
            scrollState = scrollState,
            icon = R.drawable.search,
            onClick = onSearchClick
        )
    }
}
