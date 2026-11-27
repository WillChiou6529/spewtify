package app.vitune.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vitune.android.utils.medium
import app.vitune.android.utils.bold
import androidx.compose.ui.unit.sp
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.shimmer
import kotlin.random.Random

@Composable
fun Header(
    title: String,
    modifier: Modifier = Modifier,
    actionsContent: @Composable RowScope.() -> Unit = {}
) = Header(
    modifier = modifier,
    titleContent = {
        // Spewtify：Spotify 式靠左粗體大標題；標題是空字串就不畫那一行
        if (title.isNotEmpty()) BasicText(
            text = title,
            style = LocalAppearance.current.typography.xxl.bold.copy(fontSize = 26.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    },
    actionsContent = actionsContent
)

@Composable
fun Header(
    titleContent: @Composable () -> Unit,
    actionsContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) = Column(
    // Spewtify：標題靠左在上、操作按鈕一排在下（原本是靠右疊在一起）
    modifier = modifier
        .padding(horizontal = 16.dp)
        .padding(top = 8.dp)
        .fillMaxWidth()
) {
    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
        titleContent()
    }

    // 操作列沒有任何內容時高度為 0，有內容時至少 48dp
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                content = actionsContent
            )
        }
    ) { measurables, constraints ->
        // 不用固有高度（intrinsic）判斷：操作列裡有 LazyRow 之類的元件時，問固有高度會直接閃退
        val placeable = measurables.firstOrNull()?.measure(constraints.copy(minHeight = 0))
        if (placeable == null || placeable.height == 0) layout(constraints.minWidth, 0) {}
        else {
            val height = maxOf(placeable.height, minOf(48.dp.roundToPx(), constraints.maxHeight))
            layout(placeable.width, height) { placeable.placeRelative(0, (height - placeable.height) / 2) }
        }
    }
}

@Composable
fun HeaderPlaceholder(modifier: Modifier = Modifier) = Column(
    // 版型跟 Header 一致（靠左、上 8dp、標題列＋48dp 操作列空間），載入完成不跳動
    modifier = modifier
        .padding(horizontal = 16.dp)
        .padding(top = 8.dp)
        .fillMaxWidth()
) {
    val (colorPalette, typography) = LocalAppearance.current
    val text = remember { List(Random.nextInt(4, 16)) { " " }.joinToString(separator = "") }

    Box(
        modifier = Modifier
            .background(colorPalette.shimmer)
            .fillMaxWidth(remember { 0.25f + Random.nextFloat() * 0.5f })
    ) {
        BasicText(
            text = text,
            style = typography.xxl.bold.copy(fontSize = 26.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Spacer(modifier = Modifier.height(48.dp))
}
