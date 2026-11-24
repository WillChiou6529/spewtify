package app.vitune.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.models.TagCount
import app.vitune.android.tagging.tagDisplayName
import app.vitune.android.utils.color
import app.vitune.android.utils.medium
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.surface
import app.vitune.core.ui.utils.roundedShape
import app.vitune.providers.tags.Tag
import app.vitune.providers.tags.TagKind

/**
 * 收藏頁的曲風／心情篩選列：「全部」＋各標籤（附首數）。曲風排前面、心情排後面，各自依首數多到少。
 * selected = null 代表全部。
 */
@Composable
fun TagChipRow(
    tags: List<TagCount>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val ordered = tags
        .filter { Tag.fromId(it.tag) != null }
        .sortedWith(
            compareBy<TagCount> { if (Tag.fromId(it.tag)?.kind == TagKind.GENRE) 0 else 1 }
                .thenByDescending { it.count }
        )

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            TagChip(text = stringResource(R.string.all_tags), isSelected = selected == null) { onSelect(null) }
        }
        items(items = ordered, key = { it.tag }) { tagCount ->
            TagChip(
                text = "${tagDisplayName(tagCount.tag)} ${tagCount.count}",
                isSelected = selected == tagCount.tag
            ) { onSelect(if (selected == tagCount.tag) null else tagCount.tag) }
        }
    }
}

@Composable
private fun TagChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current

    BasicText(
        text = text,
        style = typography.xxs.medium.color(if (isSelected) colorPalette.onAccent else colorPalette.text),
        modifier = Modifier
            .clip(16.dp.roundedShape)
            .clickable(onClick = onClick)
            .background(if (isSelected) colorPalette.accent else colorPalette.surface)
            .padding(vertical = 8.dp, horizontal = 14.dp)
    )
}
