package app.vitune.android.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.core.ui.LocalAppearance

/** Spotify 綠，Scaffold 的選中標籤也用同一個。 */
internal val SpotifyGreen = Color(0xFF1ED760)

/** Spotify 專輯／播放清單頁的右側「隨機播放＋綠色大圓播放鍵」。 */
@Composable
fun SpotifyPlayRow(
    enabled: Boolean,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    shuffleEnabled: Boolean = enabled
) {
    val colorPalette = LocalAppearance.current.colorPalette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .alpha(if (shuffleEnabled) 1f else 0.4f)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .clickable(enabled = shuffleEnabled, onClick = onShuffle)
        ) {
            Image(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = stringResource(R.string.shuffle),
                colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.4f)
                .size(56.dp)
                .clip(CircleShape)
                .background(SpotifyGreen)
                .clickable(enabled = enabled, onClick = onPlay)
        ) {
            Image(
                painter = painterResource(R.drawable.play),
                contentDescription = stringResource(R.string.play),
                colorFilter = ColorFilter.tint(Color.Black),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
