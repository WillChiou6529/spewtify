package app.vitune.android.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import app.vitune.android.utils.secondary
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.R
import app.vitune.android.charts.ChartRegion
import app.vitune.android.charts.Charts
import app.vitune.android.preferences.UIStatePreferences
import app.vitune.android.tagging.catching
import app.vitune.android.ui.components.themed.ArtistCircleRow
import app.vitune.android.ui.components.themed.ChartCardRow
import app.vitune.android.ui.components.themed.GenreTileGrid
import app.vitune.android.ui.items.AlbumItem
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.compose.persist.persist
import app.vitune.core.ui.Dimensions
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.roundedShape
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.requests.discoverPage

/**
 * Spotify 式「搜尋」分頁：搜尋框 → 排行榜（可切國家）＋熱門藝人 → 發掘新音樂 → 瀏覽全部（彩色曲風格子）。
 */
@Composable
fun HomeSearch(
    onSearchClick: () -> Unit,
    onPlaylistClick: (Innertube.PlaylistItem) -> Unit,
    onArtistClick: (Innertube.ArtistItem) -> Unit,
    onAlbumClick: (String) -> Unit,
    onMoodClick: (Innertube.Mood.Item) -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()
    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 28.dp, bottom = 10.dp)
        .padding(endPaddingValues)

    var discoverPage by persist<Result<Innertube.DiscoverPage>>("home/discovery")
    LaunchedEffect(Unit) {
        if (discoverPage?.isSuccess != true) discoverPage = Innertube.discoverPage()
    }

    val region = ChartRegion.fromCode(UIStatePreferences.chartsCountry)
    // null＝載入中；切國家時先換成新國家的快取（沒有就顯示載入中），不會先顯示上一國的榜單
    var chartsRetry by remember { mutableIntStateOf(0) }
    val chartsResult by produceState(
        initialValue = Charts.peek(region)?.let { Result.success(it) },
        region,
        chartsRetry
    ) {
        value = Charts.peek(region)?.let { Result.success(it) }
        value = catching { Charts.page(region, force = chartsRetry > 0) }.getOrNull()?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("charts ${region.code}"))
    }

    Column(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(windowInsets.only(WindowInsetsSides.Vertical).asPaddingValues())
    ) {
        BasicText(
            text = stringResource(R.string.nav_search),
            style = typography.xxl.semiBold.copy(fontSize = 26.sp),
            modifier = Modifier
                .padding(endPaddingValues)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 12.dp)
        )

        // 搜尋框（點了進原本的搜尋畫面）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(endPaddingValues)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(8.dp.roundedShape)
                .background(Color.White)
                .clickable(onClick = onSearchClick)
                .padding(horizontal = 12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color(0xFF121212)),
                modifier = Modifier.size(22.dp)
            )
            BasicText(
                text = stringResource(R.string.search_box_hint),
                style = typography.s.semiBold.color(Color(0xFF535353)),
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        // 排行榜（國家切換）
        BasicText(
            text = stringResource(R.string.charts),
            style = typography.l.semiBold.copy(fontSize = 22.sp),
            modifier = sectionTextModifier
        )
        LazyRow(contentPadding = endPaddingValues) {
            items(items = ChartRegion.entries, key = { it.code }) { r ->
                val selected = r == region
                BasicText(
                    text = r.displayName(),
                    style = typography.xs.semiBold.color(if (selected) Color.Black else colorPalette.text),
                    modifier = Modifier
                        .padding(start = 16.dp, bottom = 12.dp)
                        .clip(16.dp.roundedShape)
                        .background(if (selected) Color(0xFF1ED760) else colorPalette.background2)
                        .clickable { UIStatePreferences.chartsCountry = r.code }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        val chartsPage = chartsResult?.getOrNull()
        when {
            chartsResult == null -> Box(
                modifier = Modifier
                    .padding(endPaddingValues)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(8.dp.roundedShape)
                    .background(colorPalette.background1)
            )

            chartsPage == null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(endPaddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.charts_failed),
                    style = typography.xs.secondary,
                    modifier = Modifier.weight(1f)
                )
                BasicText(
                    text = stringResource(R.string.retry),
                    style = typography.xs.semiBold.color(Color.Black),
                    modifier = Modifier
                        .clip(16.dp.roundedShape)
                        .background(Color(0xFF1ED760))
                        .clickable { chartsRetry++ }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        chartsPage?.let { page ->
            ChartCardRow(
                cards = Charts.cardsOf(region, page),
                onClick = onPlaylistClick,
                contentPadding = endPaddingValues
            )
            if (page.artists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.popular_artists),
                    style = typography.l.semiBold.copy(fontSize = 22.sp),
                    modifier = sectionTextModifier
                )
                ArtistCircleRow(
                    artists = page.artists.take(20),
                    onClick = onArtistClick,
                    contentPadding = endPaddingValues
                )
            }
        }

        discoverPage?.getOrNull()?.let { page ->
            if (page.newReleaseAlbums.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.discover_new_music),
                    style = typography.l.semiBold.copy(fontSize = 22.sp),
                    modifier = sectionTextModifier
                )
                LazyRow(contentPadding = endPaddingValues) {
                    items(items = page.newReleaseAlbums, key = { it.key }) {
                        AlbumItem(
                            album = it,
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true,
                            modifier = Modifier.clickable(onClick = { onAlbumClick(it.key) })
                        )
                    }
                }
            }

            if (page.moods.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.browse_all),
                    style = typography.l.semiBold.copy(fontSize = 22.sp),
                    modifier = sectionTextModifier
                )
                GenreTileGrid(
                    moods = page.moods,
                    onClick = onMoodClick,
                    modifier = Modifier.padding(endPaddingValues)
                )
            }
        }
    }
}
