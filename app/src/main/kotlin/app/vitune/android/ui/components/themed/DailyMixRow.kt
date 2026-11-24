package app.vitune.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.tagging.DailyMix
import app.vitune.android.tagging.tagDisplayName
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.thumbnail
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.px
import app.vitune.core.ui.utils.roundedShape
import coil3.compose.AsyncImage

private val CardSize = 148.dp

/** 首頁「每日 Mix」卡片列：封面用該 Mix 第一首歌，每個分類一個固定色調當底色。 */
@Composable
fun DailyMixRow(
    mixes: List<DailyMix>,
    onClick: (DailyMix) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) = LazyRow(modifier = modifier, contentPadding = contentPadding) {
    items(items = mixes, key = { it.tag }) { mix ->
        DailyMixCard(mix = mix, onClick = { onClick(mix) }, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun DailyMixCard(mix: DailyMix, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (_, typography) = LocalAppearance.current
    val hue = ((mix.tag.hashCode() % 360) + 360) % 360
    val tint = Color.hsv(hue.toFloat(), 0.55f, 0.55f)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(CardSize)
                .clip(12.dp.roundedShape)
                .background(tint)
                .clickable(onClick = onClick)
        ) {
            AsyncImage(
                model = mix.songs.firstOrNull()?.thumbnailUrl?.thumbnail(CardSize.px),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.95f))))
            )
            BasicText(
                text = stringResource(R.string.format_daily_mix, tagDisplayName(mix.tag)),
                style = typography.s.semiBold.color(Color.White),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        }
        BasicText(
            text = stringResource(R.string.format_daily_mix_songs, mix.songs.size),
            style = typography.xxs.color(LocalAppearance.current.colorPalette.textSecondary),
            modifier = Modifier.padding(top = 6.dp, start = 2.dp)
        )
    }
}
