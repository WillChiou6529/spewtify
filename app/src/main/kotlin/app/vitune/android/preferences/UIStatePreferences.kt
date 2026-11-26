package app.vitune.android.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.vitune.android.GlobalPreferencesHolder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object UIStatePreferences : GlobalPreferencesHolder() {
    var homeScreenTabIndex by int(0)
    /** Spewtify：「你的音樂庫」上方標籤（0 歌曲、1 播放清單、2 藝人、3 專輯、4 本機） */
    var libraryTabIndex by int(0)
    /** Spewtify：搜尋頁排行榜選的國家（TW、ZZ＝全球、KR、JP、US） */
    var chartsCountry by string("TW")
    var searchResultScreenTabIndex by int(0)

    var artistScreenTabIndexProperty = int(0)
    var artistScreenTabIndex by artistScreenTabIndexProperty

    var playlistsAsGrid by boolean(true)

    private var visibleTabs by json(mapOf<String, List<String>>())

    @Composable
    fun mutableTabStateOf(
        key: String,
        default: ImmutableList<String> = persistentListOf()
    ): MutableState<ImmutableList<String>> = remember(key, default, visibleTabs) {
        mutableStateOf(
            value = visibleTabs.getOrDefault(key, default).toImmutableList(),
            policy = object : SnapshotMutationPolicy<ImmutableList<String>> {
                override fun equivalent(
                    a: ImmutableList<String>,
                    b: ImmutableList<String>
                ): Boolean {
                    val eq = a == b
                    if (!eq) visibleTabs += key to b
                    return eq
                }
            }
        )
    }
}
