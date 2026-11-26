package app.vitune.android.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.preferences.UIStatePreferences
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import kotlinx.coroutines.flow.MutableSharedFlow

/** Spotify 式底部導覽：首頁／搜尋／你的音樂庫。放在全域播放器層（MainApplication），迷你播放列在它上面。 */
object SpewtifyNav {
    val Height = 56.dp

    /** 點底部分頁時發出：首頁的路由回到根頁面（從專輯、藝人頁跳回分頁）。 */
    val resetToRoot = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    const val TAB_HOME = 0
    const val TAB_SEARCH = 1
    const val TAB_LIBRARY = 2

    /** 舊版存的分頁編號（0～6）對應到新的 3 個分頁。 */
    fun currentTab(): Int = when (val i = UIStatePreferences.homeScreenTabIndex) {
        in 0..2 -> i
        else -> TAB_LIBRARY
    }

    fun select(tab: Int) {
        UIStatePreferences.homeScreenTabIndex = tab
        resetToRoot.tryEmit(Unit)
    }
}

private data class NavItem(val tab: Int, val label: Int, val icon: Int)

private val items = listOf(
    NavItem(SpewtifyNav.TAB_HOME, R.string.nav_home, R.drawable.home),
    NavItem(SpewtifyNav.TAB_SEARCH, R.string.nav_search, R.drawable.search),
    NavItem(SpewtifyNav.TAB_LIBRARY, R.string.nav_library, R.drawable.library)
)

@Composable
fun SpewtifyBottomBar(
    bottomInset: Dp,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val selected = SpewtifyNav.currentTab()

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(colorPalette.background0.copy(alpha = 0.92f), colorPalette.background0)
                )
            )
            .padding(bottom = bottomInset)
            .height(SpewtifyNav.Height)
    ) {
        items.forEach { item ->
            val isSelected = item.tab == selected
            val tint = if (isSelected) colorPalette.text else colorPalette.textDisabled
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { SpewtifyNav.select(item.tab) }
                    )
            ) {
                Image(
                    painter = painterResource(item.icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(24.dp)
                )
                BasicText(
                    text = stringResource(item.label),
                    style = (if (isSelected) typography.xxs.semiBold else typography.xxs).color(tint),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
