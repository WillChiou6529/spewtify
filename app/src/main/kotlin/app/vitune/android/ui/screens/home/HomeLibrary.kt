package app.vitune.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.R
import app.vitune.android.preferences.UIStatePreferences
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.roundedShape

/**
 * Spotify 式「你的音樂庫」：標題＋上方小標籤切換 歌曲／播放清單／藝人／專輯／本機，
 * 每個標籤沿用原本的畫面（它們自己的上方安全區由這裡處理）。
 */
@Composable
fun HomeLibrary(
    songs: @Composable () -> Unit,
    playlists: @Composable () -> Unit,
    artists: @Composable () -> Unit,
    albums: @Composable () -> Unit,
    local: @Composable () -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val stateHolder = rememberSaveableStateHolder()
    val tabs = listOf(R.string.songs, R.string.playlists, R.string.artists, R.string.albums, R.string.local)
    val selected = UIStatePreferences.libraryTabIndex.coerceIn(0, tabs.lastIndex)

    Column(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier.padding(
                windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
            )
        ) {
            BasicText(
                text = stringResource(R.string.nav_library),
                style = typography.xxl.semiBold.copy(fontSize = 26.sp),
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
            )
            LazyRow {
                itemsIndexed(tabs) { index, label ->
                    val isSelected = index == selected
                    BasicText(
                        text = stringResource(label),
                        style = typography.xs.semiBold.color(if (isSelected) Color.Black else colorPalette.text),
                        modifier = Modifier
                            .padding(start = 16.dp, bottom = 4.dp)
                            .clip(16.dp.roundedShape)
                            .background(if (isSelected) Color(0xFF1ED760) else colorPalette.background2)
                            .clickable { UIStatePreferences.libraryTabIndex = index }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 子畫面不要再加一次狀態列高度
        CompositionLocalProvider(
            LocalPlayerAwareWindowInsets provides windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                stateHolder.SaveableStateProvider(key = selected) {
                    when (selected) {
                        0 -> songs()
                        1 -> playlists()
                        2 -> artists()
                        3 -> albums()
                        else -> local()
                    }
                }
            }
        }
    }
}
