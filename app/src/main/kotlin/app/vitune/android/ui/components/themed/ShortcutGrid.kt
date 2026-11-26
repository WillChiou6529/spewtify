package app.vitune.android.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.thumbnail
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.px
import app.vitune.core.ui.utils.roundedShape
import coil3.compose.AsyncImage

/** 首頁捷徑方塊的一格。thumbnailUrl 為 null 時顯示漸層＋圖示（例如「收藏的歌曲」）。 */
data class Shortcut(
    val key: String,
    val title: String,
    val thumbnailUrl: String?,
    val icon: Int? = null,
    val gradient: Pair<Long, Long>? = null,
    val onClick: () -> Unit
)

private val TileHeight = 56.dp

/** Spotify 式首頁最上方的 2 欄捷徑方塊（最多 8 格）。 */
@Composable
fun ShortcutGrid(
    shortcuts: List<Shortcut>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    if (shortcuts.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
    ) {
        shortcuts.take(8).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { ShortcutTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShortcutTile(shortcut: Shortcut, modifier: Modifier = Modifier) {
    val (colorPalette, typography) = LocalAppearance.current
    val thumbnailPx = TileHeight.px

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(TileHeight)
            .clip(6.dp.roundedShape)
            .background(colorPalette.background1)
            .clickable(onClick = shortcut.onClick)
    ) {
        if (shortcut.thumbnailUrl != null) AsyncImage(
            model = shortcut.thumbnailUrl.thumbnail(thumbnailPx),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(TileHeight)
                .background(colorPalette.background2)
        ) else Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(TileHeight)
                .background(
                    shortcut.gradient
                        ?.let { (from, to) -> Brush.linearGradient(listOf(Color(from), Color(to))) }
                        ?: Brush.linearGradient(listOf(colorPalette.background2, colorPalette.background2))
                )
        ) {
            shortcut.icon?.let {
                Image(
                    painter = painterResource(it),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        BasicText(
            text = shortcut.title,
            style = typography.xxs.semiBold.copy(color = colorPalette.text),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
    }
}
