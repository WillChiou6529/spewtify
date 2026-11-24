package app.vitune.android.models

import androidx.compose.runtime.Immutable

/** 某個標籤在收藏裡有幾首歌（收藏頁標籤列用）。 */
@Immutable
data class TagCount(
    val tag: String,
    val count: Int
)
