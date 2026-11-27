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
import androidx.compose.ui.draw.clip
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
 * 首頁頂部的心情／情境小標籤（Spotify「所有／音樂／Podcast」那排）：點一個＝首頁只顯示這個心情的內容（篩選），
 * 不會自動換歌；要播就按篩選後畫面上的綠色播放鍵。「所有」＝回到完整首頁；再點一次選中的標籤也會回到「所有」。
 */
@Composable
fun VibeRow(
    selected: Vibe?,
    onSelect: (Vibe?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) = LazyRow(modifier = modifier, contentPadding = contentPadding) {
    item(key = "all") {
        Chip(
            text = stringResource(R.string.vibe_all),
            selected = selected == null,
            onClick = { onSelect(null) },
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    items(items = Vibe.entries, key = { it.id }) { vibe ->
        Chip(
            text = vibe.displayName(),
            selected = selected == vibe,
            onClick = { onSelect(if (selected == vibe) null else vibe) },
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Spotify 膠囊標籤：灰底白字，選中＝綠底黑字。 */
@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp)
            .clip(18.dp.roundedShape)
            .background(if (selected) Color(0xFF1ED760) else colorPalette.background2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        BasicText(
            text = text,
            style = typography.xs.semiBold.color(if (selected) Color.Black else colorPalette.text)
        )
    }
}
