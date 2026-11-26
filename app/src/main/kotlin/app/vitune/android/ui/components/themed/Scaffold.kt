package app.vitune.android.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.R
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.roundedShape

/**
 * Spewtify：Spotify 式頁面外框——左上 ←（或頁面給的圖示）＋一排小標籤切分頁（只有一頁時不顯示），
 * 取代 ViTune 左側直排分頁欄。子畫面不用再自己加狀態列高度（這裡已處理）。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Scaffold(
    key: String,
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabChange: (Int) -> Unit,
    tabColumnContent: TabsBuilder.() -> Unit,
    modifier: Modifier = Modifier,
    tabsEditingTitle: String = stringResource(R.string.tabs),
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val insets = LocalPlayerAwareWindowInsets.current
    val tabs = TabsBuilder.rememberTabs(tabColumnContent)

    Column(
        modifier = modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues())
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Image(
                painter = painterResource(topIconButtonId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.text),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onTopIconButtonClick)
                    .padding(12.dp)
                    .size(22.dp)
            )
            if (tabs.size > 1) LazyRow {
                itemsIndexed(items = tabs, key = { _, tab -> tab.key }) { index, tab ->
                    val selected = index == tabIndex
                    BasicText(
                        text = tab.title(),
                        style = typography.xs.semiBold.color(if (selected) Color.Black else colorPalette.text),
                        modifier = Modifier
                            .padding(start = if (index == 0) 4.dp else 8.dp, end = if (index == tabs.lastIndex) 16.dp else 0.dp)
                            .clip(17.dp.roundedShape)
                            .background(if (selected) Color(0xFF1ED760) else colorPalette.background2)
                            .clickable { onTabChange(index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        CompositionLocalProvider(
            LocalPlayerAwareWindowInsets provides insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        ) {
            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                content = content,
                label = ""
            )
        }
    }
}
