package app.vitune.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.roundedShape

/**
 * 首頁「現在想聽什麼？」：一排小標籤（先心情、後情境），點一個就產生整條歌單並開始播。
 * loading＝正在產生的那個（顯示「產生中…」，其他暫時變淡、不能點）。
 */
@Composable
fun VibeRow(
    loading: Vibe?,
    onClick: (Vibe) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) = LazyRow(modifier = modifier, contentPadding = contentPadding) {
    items(items = Vibe.entries, key = { it.id }) { vibe ->
        VibeChip(
            vibe = vibe,
            isLoading = loading == vibe,
            isDimmed = loading != null && loading != vibe,
            onClick = { if (loading == null) onClick(vibe) },
            modifier = Modifier.padding(start = if (vibe == Vibe.entries.first()) 16.dp else 8.dp)
        )
    }
}

@Composable
private fun VibeChip(
    vibe: Vibe,
    isLoading: Boolean,
    isDimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = LocalAppearance.current.typography

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp)
            .alpha(if (isDimmed) 0.4f else 1f)
            .clip(18.dp.roundedShape)
            .background(Brush.linearGradient(listOf(Color(vibe.colorFrom), Color(vibe.colorTo))))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        BasicText(
            text = if (isLoading) stringResource(R.string.vibe_generating) else vibe.displayName(),
            style = typography.xs.semiBold.color(Color.White)
        )
    }
}
