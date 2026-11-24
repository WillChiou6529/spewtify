package app.vitune.providers.tags

import app.vitune.providers.utils.runCatchingCancellable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Last.fm 標籤查詢（https://www.last.fm/api）。API key 由使用者在 App 設定頁填入，不寫進程式碼。
 * 回傳已正規化成 [Tag] 的標籤；Last.fm 的 count 是 0～100 的相對熱度。
 */
object LastFm {
    private const val MIN_COUNT = 15

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
            install(UserAgent) { agent = "Spewtify (https://github.com/WillChiou6529/spewtify)" }
            defaultRequest { url("https://ws.audioscrobbler.com/2.0/") }
            expectSuccess = false
        }
    }

    @Serializable
    internal data class TopTagsResponse(val toptags: TopTags? = null, val error: Int? = null) {
        @Serializable
        data class TopTags(val tag: List<RawTag> = emptyList())

        @Serializable
        data class RawTag(val name: String, val count: Int = 0)
    }

    private suspend fun topTags(apiKey: String, method: String, artist: String, track: String?) =
        client.get {
            parameter("method", method)
            parameter("artist", artist)
            if (track != null) parameter("track", track)
            parameter("autocorrect", 1)
            parameter("api_key", apiKey)
            parameter("format", "json")
        }.body<TopTagsResponse>()

    internal fun TopTagsResponse.normalized(scale: Float): List<WeightedTag> =
        toptags?.tag.orEmpty()
            .filter { it.count >= MIN_COUNT }
            .mapNotNull { raw -> TagNormalizer.normalize(raw.name)?.let { WeightedTag(it, scale * raw.count / 100f) } }

    /** 先查歌曲標籤，沒有就退回歌手標籤（權重打 8 折）。 */
    suspend fun tagsFor(apiKey: String, artist: String, title: String): Result<List<WeightedTag>>? =
        runCatchingCancellable {
            val track = topTags(apiKey, "track.gettoptags", artist, title)
            check(track.error == null || track.error == 6) { "Last.fm error ${track.error}" }   // 6 = 查無此歌
            val fromTrack = track.normalized(1f)
            if (fromTrack.isNotEmpty()) return@runCatchingCancellable fromTrack.pickTop()

            val byArtist = topTags(apiKey, "artist.gettoptags", artist, null)
            check(byArtist.error == null || byArtist.error == 6) { "Last.fm error ${byArtist.error}" }
            byArtist.normalized(0.8f).pickTop()
        }
}
