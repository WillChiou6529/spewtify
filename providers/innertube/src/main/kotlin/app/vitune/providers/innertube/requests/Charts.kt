package app.vitune.providers.innertube.requests

import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.BrowseResponse
import app.vitune.providers.innertube.models.MusicResponsiveListItemRenderer
import app.vitune.providers.innertube.models.bodies.BrowseBody
import app.vitune.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * YouTube Music 排行榜（Spewtify）：某個國家的官方榜單歌單（百大熱門、本日熱播…）＋熱門藝人。
 * country：ISO 兩碼（TW、KR、JP、US），全球是 "ZZ"。
 */
data class ChartsPage(
    /** 第一個榜單區（影片排行榜）：例如 20 大發燒、本日熱播、百大熱門（百大通常在最後） */
    val playlists: List<Innertube.PlaylistItem>,
    /** 其他榜單區（例如美國的分類型前 50 名） */
    val genrePlaylists: List<Innertube.PlaylistItem>,
    val artists: List<Innertube.ArtistItem>
)

suspend fun Innertube.charts(country: String) = runCatchingCancellable {
    val response = client.post(BROWSE) {
        setBody(
            BrowseBody(
                browseId = "FEmusic_charts",
                formData = BrowseBody.FormData(selectedValues = listOf(country))
            )
        )
    }.body<BrowseResponse>()

    val shelves = response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.mapNotNull { it.musicCarouselShelfRenderer }
        .orEmpty()

    val playlistShelves = shelves
        .map { shelf ->
            shelf.contents.orEmpty()
                .mapNotNull { it.musicTwoRowItemRenderer?.toItem() as? Innertube.PlaylistItem }
                .distinctBy { it.key }
        }
        .filter { it.isNotEmpty() }

    ChartsPage(
        playlists = playlistShelves.firstOrNull().orEmpty(),
        genrePlaylists = playlistShelves.drop(1).flatten().distinctBy { it.key },
        // 同一位藝人可能出現在好幾個區塊；清單用 key 當識別，重複會讓 LazyRow 當掉
        artists = shelves
            .flatMap { it.contents.orEmpty() }
            .mapNotNull { it.musicResponsiveListItemRenderer?.toChartArtist() }
            .distinctBy { it.key }
    )
}

private fun MusicResponsiveListItemRenderer.toChartArtist(): Innertube.ArtistItem? {
    val browse = navigationEndpoint?.browseEndpoint ?: return null
    if (browse.browseId?.startsWith("UC") != true) return null
    return Innertube.ArtistItem(
        info = Innertube.Info(
            name = flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text,
            endpoint = browse
        ),
        subscribersCountText = flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.joinToString("") { it.text.orEmpty() },
        thumbnail = thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()
    )
}
