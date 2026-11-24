package app.vitune.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.tagging.displayName
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.roundedShape
import app.vitune.providers.tags.Situation

private val situationColors = mapOf(
    Situation.STUDY to (Color(0xFF3B5BDB) to Color(0xFF15AABF)),
    Situation.WORKOUT to (Color(0xFFE8590C) to Color(0xFFF03E3E)),
    Situation.SLEEP to (Color(0xFF5F3DC4) to Color(0xFF212529)),
    Situation.DRIVE to (Color(0xFF2B8A3E) to Color(0xFFFAB005))
)

/** 首頁「情境」卡片列：讀書／運動／睡前／開車，顯示收藏中符合的首數，0 首的不顯示。 */
@Composable
fun SituationRow(
    counts: Map<Situation, Int>,
    onClick: (Situation) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) = LazyRow(modifier = modifier, contentPadding = contentPadding) {
    items(items = Situation.entries.filter { (counts[it] ?: 0) > 0 }, key = { it.id }) { situation ->
        SituationCard(
            situation = situation,
            count = counts[situation] ?: 0,
            onClick = { onClick(situation) },
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun SituationCard(situation: Situation, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (colorPalette, typography) = LocalAppearance.current
    val (from, to) = situationColors.getValue(situation)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(width = 132.dp, height = 84.dp)
                .clip(12.dp.roundedShape)
                .background(Brush.linearGradient(listOf(from, to)))
                .clickable(onClick = onClick)
        ) {
            BasicText(
                text = situation.displayName(),
                style = typography.m.semiBold.color(Color.White),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
        BasicText(
            text = stringResource(R.string.format_daily_mix_songs, count),
            style = typography.xxs.color(colorPalette.textSecondary),
            modifier = Modifier.padding(top = 6.dp, start = 2.dp)
        )
    }
}
