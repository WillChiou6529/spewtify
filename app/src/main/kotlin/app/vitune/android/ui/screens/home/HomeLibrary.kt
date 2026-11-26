package app.vitune.android.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vitune.android.Database
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.R
import app.vitune.android.models.Playlist
import app.vitune.android.query
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.thumbnail
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.px
import app.vitune.core.ui.utils.roundedShape
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged

private val Green = Color(0xFF1ED760)
private val Thumb = 64.dp

private enum class LibraryFilter(val label: Int) { Playlists(R.string.playlists), Artists(R.string.artists), Albums(R.string.albums) }

/** 音樂庫清單的一列（播放清單／藝人／專輯混排）。 */
private data class LibraryRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val circle: Boolean,
    val sortName: String,
    val addedAt: Long,
    val onClick: () -> Unit
)

/**
 * Spotify 式「你的音樂庫」：頂列（設定頭像、標題、搜尋、＋新增播放清單）→ 標籤（播放清單／藝人／專輯）
 * → 排序（最近新增／字母順序）→ 清單（固定「已按讚的歌曲」「已下載」在最上面，其餘混排）。
 */
@Composable
fun HomeLibrary(
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onOfflineClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val insets = LocalPlayerAwareWindowInsets.current

    var filter by rememberSaveable { mutableStateOf<LibraryFilter?>(null) }
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    val playlists by remember { Database.playlistPreviewsByDateAddedDesc().distinctUntilChanged() }
        .collectAsState(initial = emptyList())
    val artists by remember { Database.artistsByRowIdDesc().distinctUntilChanged() }
        .collectAsState(initial = emptyList())
    val albums by remember { Database.albumsByRowIdDesc().distinctUntilChanged() }
        .collectAsState(initial = emptyList())
    val likedCount by remember { Database.likedCount().distinctUntilChanged() }.collectAsState(initial = 0)

    val playlistLabel = stringResource(R.string.library_type_playlist)
    val artistLabel = stringResource(R.string.library_type_artist)
    val albumLabel = stringResource(R.string.library_type_album)
    val songsFormat = stringResource(R.string.format_library_song_count)

    val rows = buildList {
        if (filter == null || filter == LibraryFilter.Playlists) playlists.forEachIndexed { i, p ->
            add(
                LibraryRow(
                    key = "p:${p.id}", title = p.name, subtitle = "$playlistLabel • ${songsFormat.format(p.songCount)}",
                    thumbnailUrl = p.thumbnail, circle = false, sortName = p.name.lowercase(),
                    addedAt = Long.MAX_VALUE - i, onClick = { onPlaylistClick(p.id) }
                )
            )
        }
        if (filter == null || filter == LibraryFilter.Artists) artists.forEach { a ->
            add(
                LibraryRow(
                    key = "a:${a.id}", title = a.name.orEmpty(), subtitle = artistLabel,
                    thumbnailUrl = a.thumbnailUrl, circle = true, sortName = a.name.orEmpty().lowercase(),
                    addedAt = a.bookmarkedAt ?: 0L, onClick = { onArtistClick(a.id) }
                )
            )
        }
        if (filter == null || filter == LibraryFilter.Albums) albums.forEach { al ->
            add(
                LibraryRow(
                    key = "al:${al.id}", title = al.title.orEmpty(),
                    subtitle = listOfNotNull(albumLabel, al.authorsText?.takeIf { it.isNotBlank() }).joinToString(" • "),
                    thumbnailUrl = al.thumbnailUrl, circle = false, sortName = al.title.orEmpty().lowercase(),
                    addedAt = al.bookmarkedAt ?: 0L, onClick = { onAlbumClick(al.id) }
                )
            )
        }
    }.let { list -> if (alphabetical) list.sortedBy { it.sortName } else list.sortedByDescending { it.addedAt } }

    if (creating) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        onDismiss = { creating = false },
        onAccept = { name ->
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) query { Database.insert(Playlist(name = trimmed)) }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette.background0)
            .padding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues())
    ) {
        // 頂列
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colorPalette.background2)
                    .clickable(onClick = onSettingsClick)
            ) {
                Image(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.text),
                    modifier = Modifier.size(18.dp)
                )
            }
            BasicText(
                text = stringResource(R.string.nav_library),
                style = typography.l.semiBold.copy(fontSize = 24.sp, color = colorPalette.text),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            HeaderIcon(R.drawable.search, onSearchClick)
            HeaderIcon(R.drawable.add, { creating = true })
        }

        // 標籤：再點一次取消篩選
        LazyRow(modifier = Modifier.padding(bottom = 8.dp)) {
            items(items = LibraryFilter.entries, key = { it.name }) { f ->
                val selected = filter == f
                BasicText(
                    text = stringResource(f.label),
                    style = typography.xs.semiBold.color(if (selected) Color.Black else colorPalette.text),
                    modifier = Modifier
                        .padding(start = if (f.ordinal == 0) 16.dp else 8.dp)
                        .clip(17.dp.roundedShape)
                        .background(if (selected) Green else colorPalette.background2)
                        .clickable { filter = if (selected) null else f }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        LazyColumn(
            contentPadding = insets.only(WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            // 排序
            item(key = "sort") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { alphabetical = !alphabetical }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.swap_vertical),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.text),
                        modifier = Modifier.size(16.dp)
                    )
                    BasicText(
                        text = stringResource(if (alphabetical) R.string.library_sort_alphabetical else R.string.library_sort_recent),
                        style = typography.xs.semiBold.color(colorPalette.text),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (filter == null || filter == LibraryFilter.Playlists) {
                item(key = "liked") {
                    PinnedRow(
                        title = stringResource(R.string.liked_songs),
                        subtitle = "$playlistLabel • ${songsFormat.format(likedCount)}",
                        icon = R.drawable.heart,
                        gradient = Brush.linearGradient(listOf(Color(0xFF4527A0), Color(0xFF8FA6E8))),
                        onClick = onFavoritesClick
                    )
                }
                item(key = "offline") {
                    PinnedRow(
                        title = stringResource(R.string.library_downloaded),
                        subtitle = playlistLabel,
                        icon = R.drawable.download,
                        gradient = Brush.linearGradient(listOf(Color(0xFF0D5C3A), Color(0xFF1ED760))),
                        onClick = onOfflineClick
                    )
                }
            }

            items(items = rows, key = { it.key }) { row -> LibraryItemRow(row) }

            item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HeaderIcon(icon: Int, onClick: () -> Unit) {
    val colorPalette = LocalAppearance.current.colorPalette
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(colorPalette.text),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(10.dp)
            .size(24.dp)
    )
}

@Composable
private fun PinnedRow(title: String, subtitle: String, icon: Int, gradient: Brush, onClick: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Thumb)
                .clip(4.dp.roundedShape)
                .background(gradient)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(24.dp)
            )
        }
        RowTexts(title, subtitle, pinned = true)
    }
}

@Composable
private fun LibraryItemRow(row: LibraryRow) {
    val colorPalette = LocalAppearance.current.colorPalette
    val shape: Shape = if (row.circle) CircleShape else 4.dp.roundedShape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        AsyncImage(
            model = row.thumbnailUrl?.thumbnail(Thumb.px),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(Thumb)
                .clip(shape)
                .background(colorPalette.background2)
        )
        RowTexts(row.title, row.subtitle, pinned = false)
    }
}

@Composable
private fun RowTexts(title: String, subtitle: String, pinned: Boolean) {
    val (colorPalette, typography) = LocalAppearance.current
    Column(modifier = Modifier.padding(start = 14.dp)) {
        BasicText(
            text = title,
            style = typography.s.color(colorPalette.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            if (pinned) Image(
                painter = painterResource(R.drawable.pin),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Green),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(12.dp)
            )
            BasicText(
                text = subtitle,
                style = typography.xs.color(colorPalette.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
