package app.vitune.android.ui.screens.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import app.vitune.android.ui.screens.GlobalRoutes
import app.vitune.android.ui.screens.Route
import app.vitune.compose.persist.PersistMapCleanup
import app.vitune.compose.routing.RouteHandler
import io.ktor.http.Url

/**
 * Spewtify：Spotify 式搜尋輸入畫面（← ＋圓角輸入框、最近播放、搜尋建議）。
 * 原本 ViTune 的「線上／音樂庫」兩分頁（OnlineSearch、LocalSongSearch）不再使用。
 */
@Route
@Composable
fun SearchScreen(
    initialText: String,
    onSearch: (String) -> Unit,
    onViewPlaylist: (String) -> Unit
) {
    val state = rememberTextFieldState(initialText = initialText)

    PersistMapCleanup(prefix = "search/")

    RouteHandler {
        GlobalRoutes()

        Content {
            SpotifySearchInput(
                state = state,
                onSearch = { q ->
                    // 貼 YouTube 播放清單網址：直接開清單
                    val isPlaylistUrl = runCatching {
                        Url(q).let {
                            it.host.endsWith("youtube.com", ignoreCase = true) &&
                                it.segments.lastOrNull()?.equals("playlist", ignoreCase = true) == true
                        }
                    }.getOrDefault(false)
                    if (isPlaylistUrl) onViewPlaylist(q) else onSearch(q)
                },
                onBack = pop,
                focused = child == null
            )
        }
    }
}
