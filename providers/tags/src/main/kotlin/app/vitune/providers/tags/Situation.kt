package app.vitune.providers.tags

/**
 * 情境分類（讀書／運動／睡前／開車）。由歌曲的曲風／心情標籤算分：
 * score = Σ（歌曲標籤權重 × 規則權重），≥ [THRESHOLD] 就歸入該情境；負的規則權重表示不適合。
 * 手動設定（SongTag 的 manual 列，tag id = [tagId]）優先於自動判斷。
 */
enum class Situation(
    val id: String,
    val zhName: String,
    val enName: String,
    private val rules: Map<Tag, Float>
) {
    STUDY("study", "讀書", "Study", mapOf(
        Tag.FOCUS to 1.0f, Tag.CLASSICAL to 0.9f, Tag.LOFI to 0.9f, Tag.JAZZ to 0.7f, Tag.CHILL to 0.6f,
        Tag.SOUNDTRACK to 0.5f,
        Tag.METAL to -1.0f, Tag.PARTY to -0.8f, Tag.WORKOUT to -0.8f, Tag.ENERGY to -0.5f, Tag.HIPHOP to -0.4f
    )),
    WORKOUT("workout", "運動", "Workout", mapOf(
        Tag.WORKOUT to 1.0f, Tag.ENERGY to 0.9f, Tag.PARTY to 0.7f, Tag.ELECTRONIC to 0.7f, Tag.HIPHOP to 0.7f,
        Tag.ROCK to 0.6f, Tag.METAL to 0.6f, Tag.KPOP to 0.4f, Tag.LATIN to 0.4f,
        Tag.SLEEP to -1.0f, Tag.SAD to -0.6f, Tag.CLASSICAL to -0.5f, Tag.BALLAD to -0.5f, Tag.CHILL to -0.4f
    )),
    SLEEP("sleep", "睡前", "Bedtime", mapOf(
        Tag.SLEEP to 1.0f, Tag.CHILL to 0.8f, Tag.CLASSICAL to 0.7f, Tag.LOFI to 0.7f, Tag.BALLAD to 0.5f,
        Tag.JAZZ to 0.5f, Tag.ROMANCE to 0.3f, Tag.FOLK to 0.3f,
        Tag.WORKOUT to -1.0f, Tag.ENERGY to -1.0f, Tag.PARTY to -1.0f, Tag.METAL to -1.0f,
        Tag.ELECTRONIC to -0.6f, Tag.HIPHOP to -0.6f, Tag.ROCK to -0.5f
    )),
    DRIVE("drive", "開車", "Driving", mapOf(
        Tag.COMMUTE to 1.0f, Tag.FEEL_GOOD to 0.8f, Tag.POP to 0.6f, Tag.ROCK to 0.6f, Tag.CPOP to 0.5f,
        Tag.ENERGY to 0.5f, Tag.INDIE to 0.5f, Tag.ELECTRONIC to 0.4f, Tag.RNB to 0.4f, Tag.JPOP to 0.4f,
        Tag.KPOP to 0.4f,
        Tag.SLEEP to -1.0f, Tag.CLASSICAL to -0.3f
    ));

    /** 存在 SongTag.tag 欄位裡的 id（加前綴，跟曲風／心情的 id 分開）。 */
    val tagId get() = PREFIX + id

    fun score(tags: Collection<WeightedTag>): Float = tags.sumOf { (rules[it.tag] ?: 0f).toDouble() * it.weight }.toFloat()

    companion object {
        const val PREFIX = "ctx_"
        const val THRESHOLD = 0.5f

        fun fromTagId(tagId: String): Situation? =
            if (tagId.startsWith(PREFIX)) entries.firstOrNull { it.tagId == tagId } else null

        /**
         * 一首歌屬於哪些情境。manual：手動設定（true＝加入、false＝排除），沒設定的才自動判斷。
         */
        fun of(tags: Collection<WeightedTag>, manual: Map<Situation, Boolean> = emptyMap()): Set<Situation> =
            entries.filter { s -> manual[s] ?: (s.score(tags) >= THRESHOLD) }.toSet()
    }
}
