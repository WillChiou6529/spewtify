package app.vitune.android.ui.screens.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vitune.android.Database
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.models.Song
import app.vitune.android.query
import app.vitune.android.utils.asMediaItem
import app.vitune.android.utils.color
import app.vitune.android.utils.forcePlay
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.thumbnail
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.px
import app.vitune.core.ui.utils.roundedShape
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.NavigationEndpoint
import app.vitune.providers.innertube.models.bodies.SearchSuggestionsBody
import app.vitune.providers.innertube.requests.searchSuggestions
import coil3.compose.AsyncImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration.Companion.milliseconds

private val Green = Color(0xFF1ED760)
private val RowThumb = 56.dp

/**
 * Spotify 式搜尋輸入畫面：上方 ← ＋灰色圓角輸入框「想聽什麼？」；
 * 還沒打字時列出「最近播放」（封面、歌名、「歌曲 • 歌手」、⊕／✓ 收藏、✕ 從紀錄移除），打字時列出搜尋建議。
 */
@OptIn(FlowPreview::class)
@Composable
fun SpotifySearchInput(
    state: TextFieldState,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    focused: Boolean
) {
    val (colorPalette, typography) = LocalAppearance.current
    val insets = LocalPlayerAwareWindowInsets.current
    val binder = LocalPlayerServiceBinder.current

    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(state) {
        snapshotFlow { state.text }
            .distinctUntilChanged()
            .debounce(150.milliseconds)
            .collect { q ->
                suggestions = if (q.isEmpty()) emptyList()
                else Innertube.searchSuggestions(SearchSuggestionsBody(input = q.toString()))?.getOrNull().orEmpty()
            }
    }

    val recent by remember { Database.recentSongs(40).distinctUntilChanged() }.collectAsState(initial = emptyList())

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        delay(300.milliseconds)
        focusRequester.requestFocus()
    }

    fun submit() {
        val q = state.text.toString().trim()
        if (q.isNotEmpty()) onSearch(q)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette.background0)
    ) {
        // 頂列：← ＋輸入框
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(colorPalette.background1)
                .padding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues())
                .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.text),
                modifier = Modifier
                    .clip(20.dp.roundedShape)
                    .clickable(onClick = onBack)
                    .padding(12.dp)
                    .size(24.dp)
                    .rotate(180f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(23.dp.roundedShape)
                    .background(colorPalette.background2)
                    .padding(start = 18.dp, end = 8.dp)
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) BasicText(
                        text = stringResource(R.string.search_box_hint),
                        style = typography.s.color(colorPalette.textSecondary),
                        maxLines = 1
                    )
                    BasicTextField(
                        state = state,
                        textStyle = typography.s.copy(color = colorPalette.text),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = { performDefaultAction ->
                            submit()
                            performDefaultAction()
                        },
                        cursorBrush = SolidColor(Green),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
                if (state.text.isNotEmpty()) Image(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.text),
                    modifier = Modifier
                        .clip(16.dp.roundedShape)
                        .clickable { state.clearText() }
                        .padding(6.dp)
                        .size(20.dp)
                )
            }
        }

        LazyColumn(
            contentPadding = insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal).asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.text.isEmpty()) {
                if (recent.isNotEmpty()) item(key = "title") {
                    BasicText(
                        text = stringResource(R.string.recently_played),
                        style = typography.l.semiBold.copy(fontSize = 22.sp, color = colorPalette.text),
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
                    )
                }
                items(items = recent, key = { "song:${it.id}" }) { song ->
                    RecentRow(
                        song = song,
                        onClick = {
                            binder?.stopRadio()
                            binder?.player?.forcePlay(song.asMediaItem)
                            binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = song.id))
                        },
                        onToggleLike = {
                            query { Database.like(song.id, if (song.likedAt == null) System.currentTimeMillis() else null) }
                        },
                        onRemove = { query { Database.clearEventsFor(song.id) } }
                    )
                }
            } else items(items = suggestions, key = { "s:$it" }) { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSearch(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier.size(20.dp)
                    )
                    BasicText(
                        text = suggestion,
                        style = typography.s.color(colorPalette.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    )
                    Image(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier
                            .clip(16.dp.roundedShape)
                            .clickable { state.setTextAndPlaceCursorAtEnd(suggestion) }
                            .padding(4.dp)
                            .size(20.dp)
                            .rotate(225f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(
    song: Song,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onRemove: () -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val liked = song.likedAt != null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = song.thumbnailUrl?.thumbnail(RowThumb.px),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(RowThumb)
                .clip(4.dp.roundedShape)
                .background(colorPalette.background2)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            BasicText(
                text = song.title,
                style = typography.s.color(colorPalette.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = stringResource(R.string.format_song_subtitle, song.artistsText.orEmpty()),
                style = typography.xs.color(colorPalette.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        // ⊕ 加入收藏／綠色 ✓ 已收藏
        Image(
            painter = painterResource(if (liked) R.drawable.check_circle else R.drawable.add_circle),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (liked) Green else colorPalette.textSecondary),
            modifier = Modifier
                .clip(16.dp.roundedShape)
                .clickable(onClick = onToggleLike)
                .padding(8.dp)
                .size(24.dp)
        )
        Image(
            painter = painterResource(R.drawable.close),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.textSecondary),
            modifier = Modifier
                .clip(16.dp.roundedShape)
                .clickable(onClick = onRemove)
                .padding(8.dp)
                .size(22.dp)
        )
    }
}
