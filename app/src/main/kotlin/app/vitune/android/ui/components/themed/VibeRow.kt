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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.tagging.Vibe
import app.vitune.android.tagging.VibeKind
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.roundedShape

/**
 * 首頁「現在想聽什麼？」：上排心情、下排情境，點一張卡就產生整條歌單並開始播。
 * loading＝正在產生的那張（顯示「產生中…」，其他卡暫時變淡、不能點）。
 */
@Composable
fun VibeRow(
    loading: Vibe?,
    onClick: (Vibe) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val (colorPalette, typography) = LocalAppearance.current

    Column(modifier = modifier) {
        VibeKind.entries.forEach { kind ->
            BasicText(
                text = stringResource(if (kind == VibeKind.MOOD) R.string.vibe_moods else R.string.vibe_situations),
                style = typography.xs.color(colorPalette.textSecondary),
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(start = 16.dp, top = 4.dp, bottom = 6.dp)
            )
            LazyRow(contentPadding = contentPadding) {
                items(items = Vibe.entries.filter { it.kind == kind }, key = { it.id }) { vibe ->
                    VibeCard(
                        vibe = vibe,
                        isLoading = loading == vibe,
                        isDimmed = loading != null && loading != vibe,
                        onClick = { if (loading == null) onClick(vibe) },
                        modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VibeCard(
    vibe: Vibe,
    isLoading: Boolean,
    isDimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = LocalAppearance.current.typography

    Box(
        modifier = modifier
            .size(width = 112.dp, height = 64.dp)
            .alpha(if (isDimmed) 0.4f else 1f)
            .clip(12.dp.roundedShape)
            .background(Brush.linearGradient(listOf(Color(vibe.colorFrom), Color(vibe.colorTo))))
            .clickable(onClick = onClick)
    ) {
        BasicText(
            text = vibe.displayName(),
            style = typography.s.semiBold.color(Color.White),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )
        if (isLoading) BasicText(
            text = stringResource(R.string.vibe_generating),
            style = typography.xxs.color(Color.White),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
    }
}
