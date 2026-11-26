package app.vitune.providers.tags

/**
 * YouTube 上的歌名常帶「(Official Music Video)」「【MV】」「feat. xxx」等雜訊，
 * 查 Last.fm 之前先清掉，提高命中率。
 */
object TitleCleaner {
    private val noiseWords = listOf(
        "official", "music video", "video", "audio", "lyric", "lyrics", "mv", "m/v", "hd", "4k",
        "live", "visualizer", "remaster", "remastered", "官方", "完整版", "高畫質", "歌詞", "動態歌詞", "版"
    )

    private val bracket = Regex("[(\\[（【「『]([^)\\]）】」』]*)[)\\]）】」』]")
    // 括號裡的「with xxx」視為合作歌手；括號外的 with 可能是歌名本身（Dancing With Myself），不刪
    private val featInBracket = Regex("^\\s*(feat\\.?|ft\\.?|featuring|with)\\s+", RegexOption.IGNORE_CASE)
    private val feat = Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE)
    private val dashSuffix = Regex("\\s+[-–—|]\\s+.*(official|mv|lyric|video|audio).*$", RegexOption.IGNORE_CASE)

    fun clean(title: String): String {
        var t = bracket.replace(title) { m ->
            val inner = m.groupValues[1].lowercase()
            if (noiseWords.any { inner.contains(it) } || featInBracket.containsMatchIn(inner)) "" else m.value
        }
        t = dashSuffix.replace(t, "")
        t = feat.replace(t, "")
        return t.replace(Regex("\\s+"), " ").trim().ifEmpty { title.trim() }
    }

    /** 「歌手A, 歌手B & 歌手C」只取第一位，Last.fm 用主歌手查比較準。 */
    fun primaryArtist(artistsText: String?): String? = artistsText
        // feat／ft／x 前後一定要有空白，否則 "Taylor Swift" 會被切成 "Taylor Swi"
        ?.split(Regex("\\s*(?:,|&|、|×)\\s*|\\s+(?:x|feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE))
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.removeSuffix(" - Topic")
}
