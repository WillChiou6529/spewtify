package app.vitune.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import app.vitune.android.ui.components.themed.SpewtifyNav
import app.vitune.core.ui.LocalAppearance
import app.vitune.android.R
import app.vitune.android.models.toUiMood
import app.vitune.android.preferences.UIStatePreferences
import app.vitune.android.ui.components.themed.Scaffold
import app.vitune.android.ui.screens.GlobalRoutes
import app.vitune.android.ui.screens.Route
import app.vitune.android.ui.screens.albumRoute
import app.vitune.android.ui.screens.artistRoute
import app.vitune.android.ui.screens.builtInPlaylistRoute
import app.vitune.android.ui.screens.builtinplaylist.BuiltInPlaylistScreen
import app.vitune.android.ui.screens.localPlaylistRoute
import app.vitune.android.ui.screens.localplaylist.LocalPlaylistScreen
import app.vitune.android.ui.screens.mood.MoodScreen
import app.vitune.android.ui.screens.mood.MoreAlbumsScreen
import app.vitune.android.ui.screens.mood.MoreMoodsScreen
import app.vitune.android.ui.screens.moodRoute
import app.vitune.android.ui.screens.pipedPlaylistRoute
import app.vitune.android.ui.screens.playlistRoute
import app.vitune.android.ui.screens.searchRoute
import app.vitune.android.ui.screens.settingsRoute
import app.vitune.compose.persist.PersistMapCleanup
import app.vitune.compose.routing.Route0
import app.vitune.compose.routing.RouteHandler

private val moreMoodsRoute = Route0("moreMoodsRoute")
private val moreAlbumsRoute = Route0("moreAlbumsRoute")

@Route
@Composable
fun HomeScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup("home/")

    RouteHandler {
        GlobalRoutes()

        localPlaylistRoute { playlistId ->
            LocalPlaylistScreen(playlistId = playlistId)
        }

        builtInPlaylistRoute { builtInPlaylist ->
            BuiltInPlaylistScreen(builtInPlaylist = builtInPlaylist)
        }

        moodRoute { mood ->
            MoodScreen(mood = mood)
        }

        moreMoodsRoute {
            MoreMoodsScreen()
        }

        moreAlbumsRoute {
            MoreAlbumsScreen()
        }

        // Spewtify：點底部導覽的分頁時，從專輯／藝人等子頁回到分頁本身
        LaunchedEffect(Unit) {
            SpewtifyNav.resetToRoot.collect { replace(null) }
        }

        Content {
            // Spewtify：Spotify 式 3 個分頁（首頁／搜尋／你的音樂庫），導覽列在 MainApplication 最底
            val currentTabIndex = SpewtifyNav.currentTab()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalAppearance.current.colorPalette.background0)
            ) {
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    val onSearchClick = { searchRoute("") }
                    when (currentTabIndex) {
                        SpewtifyNav.TAB_HOME -> QuickPicks(
                            onAlbumClick = { albumRoute(it.key) },
                            onArtistClick = { artistRoute(it.key) },
                            onPlaylistClick = {
                                playlistRoute(
                                    p0 = it.key,
                                    p1 = null,
                                    p2 = null,
                                    p3 = it.channel?.name == "YouTube Music"
                                )
                            },
                            onSearchClick = onSearchClick,
                            onFavoritesClick = {
                                builtInPlaylistRoute(app.vitune.core.data.enums.BuiltInPlaylist.Favorites)
                            },
                            onLocalPlaylistClick = { localPlaylistRoute(it) },
                            onSettingsClick = { settingsRoute() }
                        )

                        SpewtifyNav.TAB_SEARCH -> HomeSearch(
                            onSearchClick = onSearchClick,
                            onPlaylistClick = { playlistRoute(it.key, null, null, false) },
                            onArtistClick = { artistRoute(it.key) },
                            onAlbumClick = { albumRoute(it) },
                            onMoodClick = { mood -> moodRoute(mood.toUiMood()) }
                        )

                        else -> HomeLibrary(
                            onSettingsClick = { settingsRoute() },
                            onSearchClick = onSearchClick,
                            onFavoritesClick = {
                                builtInPlaylistRoute(app.vitune.core.data.enums.BuiltInPlaylist.Favorites)
                            },
                            onOfflineClick = {
                                builtInPlaylistRoute(app.vitune.core.data.enums.BuiltInPlaylist.Offline)
                            },
                            onPlaylistClick = { localPlaylistRoute(it) },
                            onArtistClick = { artistRoute(it) },
                            onAlbumClick = { albumRoute(it) }
                        )
                    }
                }
            }
        }
    }
}
