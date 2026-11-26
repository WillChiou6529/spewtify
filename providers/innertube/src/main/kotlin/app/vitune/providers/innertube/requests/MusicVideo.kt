package app.vitune.providers.innertube.requests

import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.SearchBody
import app.vitune.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** YouTube Music 搜尋「影片」分頁的 params（2026-11-26 實測可用） */
private const val VIDEOS_PARAMS = "EgWKAQIQAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"

private data class VideoHit(val videoId: String, val title: String, val type: String?)

/**
 * 找一首歌的官方 MV（Spewtify 播放畫面背景用）：用「歌手 歌名」搜影片，取第一個官方 MV（MUSIC_VIDEO_TYPE_OMV）
 * 而且標題要對得上歌名；Live 版排除（除非歌名本身就是 Live）。找不到回 null。
 */
suspend fun Innertube.officialMusicVideo(title: String, artist: String?, videoId: String) = runCatchingCancellable {
    val response = client.post(SEARCH) {
        setBody(SearchBody(query = listOfNotNull(artist, title).joinToString(" "), params = VIDEOS_PARAMS))
    }.body<JsonObject>()

    val hits = mutableListOf<VideoHit>()
    collectHits(response, hits)

    val want = normalize(title)
    val songIsLive = "live" in title.lowercase() || "現場" in title
    hits.firstOrNull { it.videoId == videoId && it.type == "MUSIC_VIDEO_TYPE_OMV" }?.videoId
        ?: hits.firstOrNull { hit ->
            val got = normalize(hit.title)
            val isLive = "live" in hit.title.lowercase() || "現場" in hit.title
            hit.type == "MUSIC_VIDEO_TYPE_OMV" &&
                want.isNotEmpty() && (want in got || got in want) &&
                (songIsLive || !isLive)
        }?.videoId
}

private fun normalize(s: String) = s.lowercase()
    .replace(Regex("[（(\\[【].*?[)）\\]】]"), "")
    .replace(Regex("[^\\p{L}\\p{N}]"), "")

private fun collectHits(element: JsonElement, out: MutableList<VideoHit>) {
    when (element) {
        is JsonObject -> {
            val renderer = element["musicResponsiveListItemRenderer"] as? JsonObject
            if (renderer != null) {
                val id = ((renderer["playlistItemData"] as? JsonObject)?.get("videoId") as? JsonPrimitive)?.contentOrNull
                if (id != null) out += VideoHit(id, firstColumnText(renderer), findString(renderer, "musicVideoType"))
                return
            }
            element.values.forEach { collectHits(it, out) }
        }
        is JsonArray -> element.forEach { collectHits(it, out) }
        else -> Unit
    }
}

private fun firstColumnText(renderer: JsonObject): String {
    val column = (renderer["flexColumns"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return ""
    val runs = (((column["musicResponsiveListItemFlexColumnRenderer"] as? JsonObject)
        ?.get("text") as? JsonObject)?.get("runs") as? JsonArray) ?: return ""
    return runs.joinToString("") { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty() }
}

private fun findString(element: JsonElement, key: String): String? = when (element) {
    is JsonObject -> (element[key] as? JsonPrimitive)?.contentOrNull
        ?: element.values.firstNotNullOfOrNull { findString(it, key) }
    is JsonArray -> element.firstNotNullOfOrNull { findString(it, key) }
    else -> null
}
