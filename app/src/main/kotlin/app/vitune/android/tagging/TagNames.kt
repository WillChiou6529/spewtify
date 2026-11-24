package app.vitune.android.tagging

import app.vitune.providers.tags.Tag
import java.util.Locale

/** 標籤顯示名稱：手機語言是中文就用中文名，其他用英文名；不認得的 id 原樣顯示。 */
fun tagDisplayName(id: String): String {
    val tag = Tag.fromId(id) ?: return id
    return if (Locale.getDefault().language == "zh") tag.zhName else tag.enName
}
