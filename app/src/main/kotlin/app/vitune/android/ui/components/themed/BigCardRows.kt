package app.vitune.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vitune.android.charts.ChartCard
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.thumbnail
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.px
import app.vitune.core.ui.utils.roundedShape
import app.vitune.providers.innertube.Innertube
import coil3.compose.AsyncImage

private val BigCard = 156.dp
private val ArtistCircle = 140.dp

/** 排行榜卡的漸層（照地區＋順序輪替，像 Spotify Top 50 的大色塊） */
private val chartGradients = listOf(
    0xFFE91E63 to 0xFFD81B60,
    0xFF7B1FA2 to 0xFF4A148C,
    0xFF00897B to 0xFF004D40,
    0xFF1E88E5 to 0xFF0D47A1,
    0xFFF4511E to 0xFFBF360C,
    0xFF5E35B1 to 0xFF311B92,
    0xFF43A047 to 0xFF1B5E20,
    0xFF6D4C41 to 0xFF3E2723
)

/** Spotify 式「排行榜」：大方形漸層卡＋大字（Top 100／今日熱播）＋地區，卡片下方是榜單名稱。 */
@Composable
fun ChartCardRow(
    cards: List<ChartCard>,
    onClick: (Innertube.PlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val (colorPalette, typography) = LocalAppearance.current
    LazyRow(modifier = modifier, contentPadding = contentPadding) {
        items(items = cards, key = { it.playlist.key }) { card ->
            val (from, to) = chartGradients[(card.region.ordinal * 3 + cards.indexOf(card)) % chartGradients.size]
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .width(BigCard)
                    .clickable { onClick(card.playlist) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(BigCard)
                        .clip(6.dp.roundedShape)
                        .background(Brush.linearGradient(listOf(Color(from), Color(to))))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(
                            text = card.headline,
                            style = typography.l.semiBold.copy(fontSize = 26.sp, color = Color.White, textAlign = TextAlign.Center)
                        )
                        Box(
                            modifier = Modifier
                                .padding(vertical = 10.dp)
                                .width(72.dp)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.6f))
                        )
                        BasicText(
                            text = card.region.displayName().uppercase(),
                            style = typography.xxs.semiBold.copy(color = Color.White, letterSpacing = 2.sp)
                        )
                    }
                }
                BasicText(
                    text = card.playlist.info?.name.orEmpty(),
                    style = typography.xxs.color(colorPalette.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** Spotify 式大方卡（「熱門專輯和單曲」等）：大封面＋粗體標題＋灰色副標。 */
data class BigSquare(val key: String, val thumbnailUrl: String?, val title: String, val subtitle: String?)

@Composable
fun BigSquareRow(
    items: List<BigSquare>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val (colorPalette, typography) = LocalAppearance.current
    val px = BigCard.px
    LazyRow(modifier = modifier, contentPadding = contentPadding) {
        items(count = items.size, key = { items[it].key }) { index ->
            val item = items[index]
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .width(BigCard)
                    .clickable { onClick(index) }
            ) {
                AsyncImage(
                    model = item.thumbnailUrl?.thumbnail(px),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(BigCard)
                        .clip(6.dp.roundedShape)
                        .background(colorPalette.background2)
                )
                BasicText(
                    text = item.title,
                    style = typography.xs.semiBold.color(colorPalette.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
                item.subtitle?.let {
                    BasicText(
                        text = it,
                        style = typography.xxs.color(colorPalette.textSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/** 電台卡的底色（Spotify 熱門電台那種粉彩色塊） */
private val radioColors = listOf(
    0xFFB8B5F5, 0xFFC5E86C, 0xFF9FD8F0, 0xFFF5B8C8, 0xFFF7D774, 0xFFA8E6CF, 0xFFFFB38A, 0xFFD0B3FF
)

/**
 * Spotify 式「熱門電台」：彩色方卡、上方「電台」標籤、中間藝人圓形頭像、左下藝人名；
 * 卡片下方一行說明。loadingKey＝正在啟動的那張（顯示淡化）。
 */
@Composable
fun RadioCardRow(
    artists: List<Innertube.ArtistItem>,
    loadingKey: String?,
    onClick: (Innertube.ArtistItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val (colorPalette, typography) = LocalAppearance.current
    val px = 96.dp.px
    LazyRow(modifier = modifier, contentPadding = contentPadding) {
        items(count = artists.size, key = { artists[it].key }) { index ->
            val artist = artists[index]
            val bg = Color(radioColors[index % radioColors.size])
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .width(BigCard)
                    .clickable { if (loadingKey == null) onClick(artist) }
            ) {
                Box(
                    modifier = Modifier
                        .size(BigCard)
                        .clip(6.dp.roundedShape)
                        .background(bg)
                        .then(if (loadingKey == artist.key) Modifier.background(Color.Black.copy(alpha = 0.25f)) else Modifier)
                ) {
                    BasicText(
                        text = androidx.compose.ui.res.stringResource(app.vitune.android.R.string.radio_label),
                        style = typography.xxs.semiBold.color(Color(0xFF121212)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                    AsyncImage(
                        model = artist.thumbnail?.url?.thumbnail(px),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(colorPalette.background2)
                    )
                    BasicText(
                        text = artist.info?.name.orEmpty(),
                        style = typography.s.semiBold.color(Color(0xFF121212)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                BasicText(
                    text = androidx.compose.ui.res.stringResource(
                        app.vitune.android.R.string.format_radio_subtitle,
                        artist.info?.name.orEmpty()
                    ),
                    style = typography.xxs.color(colorPalette.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** Spotify 式「熱門藝人」：大圓形頭像＋名字。 */
@Composable
fun ArtistCircleRow(
    artists: List<Innertube.ArtistItem>,
    onClick: (Innertube.ArtistItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val (colorPalette, typography) = LocalAppearance.current
    val px = ArtistCircle.px
    LazyRow(modifier = modifier, contentPadding = contentPadding) {
        items(items = artists, key = { it.key }) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .width(ArtistCircle)
                    .clickable { onClick(artist) }
            ) {
                AsyncImage(
                    model = artist.thumbnail?.url?.thumbnail(px),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(ArtistCircle)
                        .clip(CircleShape)
                        .background(colorPalette.background2)
                )
                BasicText(
                    text = artist.info?.name.orEmpty(),
                    style = typography.xs.semiBold.color(colorPalette.text).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

/** Spotify 式「瀏覽全部」的彩色曲風格子：2 欄、寬矩形、左上白字。 */
@Composable
fun GenreTileGrid(
    tiles: List<Pair<String, Long>>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = LocalAppearance.current.typography
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        tiles.withIndex().chunked(2).forEach { row ->
            androidx.compose.foundation.layout.Row(modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEachIndexed { i, (index, tile) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (i == 0) 0.dp else 10.dp)
                            .height(96.dp)
                            .clip(8.dp.roundedShape)
                            .background(Color(tile.second))
                            .clickable { onClick(index) }
                    ) {
                        BasicText(
                            text = tile.first,
                            style = typography.s.semiBold.color(Color.White),
                            maxLines = 2,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f).padding(start = 10.dp))
            }
        }
    }
}
