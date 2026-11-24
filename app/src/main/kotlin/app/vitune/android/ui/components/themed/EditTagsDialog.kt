package app.vitune.android.ui.components.themed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.Database
import app.vitune.android.R
import app.vitune.android.models.SongTag
import app.vitune.android.query
import app.vitune.android.tagging.Situations
import app.vitune.android.tagging.displayName
import app.vitune.android.tagging.tagDisplayName
import app.vitune.android.utils.color
import app.vitune.android.utils.medium
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.providers.tags.Situation
import app.vitune.providers.tags.Tag
import app.vitune.providers.tags.TagKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 手動編輯一首歌的情境／曲風／心情。只把「跟目前狀態不同」的項目寫成 manual 列
 * （勾選＝weight 1、取消＝weight -1），之後自動分類不會覆蓋。
 */
@Composable
fun EditTagsDialog(songId: String, onDismiss: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current
    var rows by remember { mutableStateOf<List<SongTag>?>(null) }

    LaunchedEffect(songId) {
        rows = withContext(Dispatchers.IO) { Database.allTagsOfNow(songId) }
    }

    val current = rows
    val initial: Map<String, Boolean> = remember(current) {
        if (current == null) emptyMap()
        else {
            val weights = current.associate { it.tag to it.weight }
            val situations = Situations.of(current)
            Tag.entries.associate { it.id to ((weights[it.id] ?: 0f) > 0f) } +
                Situation.entries.associate { it.tagId to (it in situations) }
        }
    }
    val checked = remember(initial) { mutableStateMapOf<String, Boolean>().apply { putAll(initial) } }

    DefaultDialog(onDismiss = onDismiss, horizontalAlignment = Alignment.Start) {
        BasicText(
            text = stringResource(R.string.edit_tags),
            style = typography.s.semiBold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (current == null) {
            BasicText(text = stringResource(R.string.please_wait), style = typography.xs.color(colorPalette.textSecondary))
        } else Column(
            modifier = Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Section(stringResource(R.string.tag_section_situations))
            Situation.entries.forEach { s ->
                CheckRow(s.displayName(), checked[s.tagId] == true) { checked[s.tagId] = !(checked[s.tagId] ?: false) }
            }
            Section(stringResource(R.string.tag_section_genres))
            Tag.entries.filter { it.kind == TagKind.GENRE }.forEach { t ->
                CheckRow(tagDisplayName(t.id), checked[t.id] == true) { checked[t.id] = !(checked[t.id] ?: false) }
            }
            Section(stringResource(R.string.tag_section_moods))
            Tag.entries.filter { it.kind == TagKind.MOOD }.forEach { t ->
                CheckRow(tagDisplayName(t.id), checked[t.id] == true) { checked[t.id] = !(checked[t.id] ?: false) }
            }
        }

        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            DialogTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
            Spacer(modifier = Modifier.width(8.dp))
            DialogTextButton(
                text = stringResource(R.string.done),
                primary = true,
                enabled = current != null,
                onClick = {
                    val changed = checked.filter { (key, value) -> initial[key] != value }
                        .map { (key, value) -> SongTag(songId, key, SongTag.SOURCE_MANUAL, if (value) 1f else -1f) }
                    if (changed.isNotEmpty()) query { Database.upsertTags(changed) }
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    val (colorPalette, typography) = LocalAppearance.current
    BasicText(
        text = title,
        style = typography.xxs.semiBold.color(colorPalette.textSecondary),
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun CheckRow(text: String, isChecked: Boolean, onToggle: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp)
    ) {
        BasicText(
            text = if (isChecked) "✓" else "",
            style = typography.xs.semiBold.color(colorPalette.accent),
            modifier = Modifier.width(24.dp)
        )
        BasicText(
            text = text,
            style = typography.xs.medium.color(if (isChecked) colorPalette.text else colorPalette.textSecondary)
        )
    }
}
