package app.vitune.android.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.Song
import app.vitune.android.tagging.OfficialPlaylists
import app.vitune.android.tagging.Vibe
import app.vitune.android.tagging.VibePlaylist
import app.vitune.android.tagging.catching
import app.vitune.android.ui.components.themed.BigSquare
import app.vitune.android.ui.components.themed.BigSquareRow
import app.vitune.android.ui.components.themed.CircularProgressIndicator
import app.vitune.android.ui.items.SongItem
import app.vitune.android.utils.asMediaItem
import app.vitune.android.utils.bold
import app.vitune.android.utils.center
import app.vitune.android.utils.forcePlayAtIndex
import app.vitune.android.utils.forcePlayFromBeginning
import app.vitune.android.utils.secondary
import app.vitune.android.utils.toast
import app.vitune.core.ui.Dimensions
import app.vitune.core.ui.LocalAppearance
import app.vitune.providers.innertube.Innertube
import kotlinx.coroutines.launch

/**
 * 首頁選了某個心情／情境小標籤時的內容（Spotify 點「音樂」標籤那樣只看這類）：
 * 大標題＋綠色播放鍵（產生約 50 首的混合歌單並播放）→ 收藏中符合的歌 → YouTube Music 官方對應歌單。
 * 點標籤本身不會換歌，要按播放鍵或點某首歌才播。
 */
@Composable
fun VibeHome(
    vibe: Vibe,
    onPlaylistClick: (Innertube.PlaylistItem) -> Unit,
    contentPadding: PaddingValues
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var generating by remember(vibe) { mutableStateOf(false) }

    val own by produceState<List<Song>?>(initialValue = null, vibe) {
        value = catching { VibePlaylist.ownSongs(vibe) }.getOrDefault(emptyList())
    }
    val playlists by produceState<List<Innertube.PlaylistItem>?>(initialValue = null, vibe) {
        value = catching { OfficialPlaylists.playlists(vibe.official) }.getOrDefault(emptyList())
    }

    val sectionTitleStyle = typography.l.bold.copy(fontSize = 22.sp, color = colorPalette.text)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = vibe.displayName(),
                style = typography.xxl.bold.copy(fontSize = 28.sp, color = colorPalette.text)
            )
            BasicText(
                text = stringResource(R.string.vibe_play_hint),
                style = typography.xs.secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF1ED760))
                .clickable(enabled = !generating) {
                    generating = true
                    scope.launch {
                        val result = try {
                            catching { VibePlaylist.build(vibe) }.getOrNull()
                        } finally {
                            generating = false
                        }
                        if (result == null || result.songs.isEmpty()) {
                            context.toast(context.getString(R.string.vibe_empty))
                            return@launch
                        }
                        if (result.freshCount == 0) context.toast(context.getString(R.string.vibe_offline))
                        binder?.stopRadio()
                        binder?.player?.forcePlayFromBeginning(result.songs.map { it.toSong().asMediaItem })
                    }
                }
        ) {
            if (generating) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Image(
                painter = painterResource(R.drawable.play),
                contentDescription = stringResource(R.string.play),
                colorFilter = ColorFilter.tint(Color.Black),
                modifier = Modifier.size(24.dp)
            )
        }
    }

    own?.takeIf { it.isNotEmpty() }?.let { songs ->
        BasicText(
            text = stringResource(R.string.vibe_from_liked),
            style = sectionTitleStyle,
            modifier = Modifier
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 8.dp)
        )
        songs.take(10).forEachIndexed { index, song ->
            SongItem(
                song = song,
                thumbnailSize = Dimensions.thumbnails.song,
                modifier = Modifier.clickable {
                    binder?.stopRadio()
                    binder?.player?.forcePlayAtIndex(songs.map(Song::asMediaItem), index)
                }
            )
        }
    }

    playlists?.takeIf { it.isNotEmpty() }?.let { list ->
        BasicText(
            text = stringResource(R.string.vibe_official_playlists),
            style = sectionTitleStyle,
            modifier = Modifier
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 28.dp, bottom = 12.dp)
        )
        BigSquareRow(
            items = list.map {
                BigSquare(
                    key = it.key,
                    thumbnailUrl = it.thumbnail?.url,
                    title = it.info?.name.orEmpty(),
                    subtitle = it.channel?.name
                )
            },
            onClick = { index -> list.getOrNull(index)?.let(onPlaylistClick) },
            contentPadding = contentPadding
        )
    }

    val ownLoaded = own
    val playlistsLoaded = playlists
    when {
        ownLoaded == null || playlistsLoaded == null -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }

        ownLoaded.isEmpty() && playlistsLoaded.isEmpty() -> BasicText(
            text = stringResource(R.string.vibe_nothing),
            style = typography.s.secondary.center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 24.dp)
        )
    }
}
