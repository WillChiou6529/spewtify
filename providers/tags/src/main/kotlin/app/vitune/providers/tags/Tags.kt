package app.vitune.providers.tags

/**
 * Spewtify 的標準分類。曲風（GENRE）與心情（MOOD）各一組固定 id，
 * 不管來源是 YouTube Music 官方分類名稱還是 Last.fm 標籤，都先正規化成這裡的 id 再存進資料庫。
 */
enum class TagKind { GENRE, MOOD }

enum class Tag(
    val id: String,
    val kind: TagKind,
    val zhName: String,
    val enName: String,
    aliases: List<String>
) {
    // ---- 曲風 ----
    CPOP("cpop", TagKind.GENRE, "華語", "C-Pop", listOf(
        "mandopop", "cantopop", "mandopop & cantopop", "c-pop", "cpop", "chinese", "chinese pop",
        "taiwanese", "taiwan", "hong kong", "hokkien", "華語", "中文", "國語", "粵語", "台語", "華語流行"
    )),
    JPOP("jpop", TagKind.GENRE, "日語", "J-Pop", listOf(
        "j-pop", "jpop", "japanese", "anime", "j-rock", "jrock", "city pop", "vocaloid", "anisong", "日語", "日文"
    )),
    KPOP("kpop", TagKind.GENRE, "韓語", "K-Pop", listOf(
        "k-pop", "kpop", "korean", "k-indie", "k-ballad", "k-rap", "韓語", "韓文"
    )),
    LOFI("lofi", TagKind.GENRE, "Lo-fi", "Lo-fi", listOf(
        "lofi", "lo-fi", "lo fi", "chillhop", "lofi hip hop", "lo-fi beats", "jazzhop", "jazz hop"
    )),
    HIPHOP("hiphop", TagKind.GENRE, "嘻哈", "Hip-hop", listOf(
        "hip-hop", "hip hop", "hiphop", "rap", "trap", "hip-hop & rap", "drill", "嘻哈", "饒舌"
    )),
    RNB("rnb", TagKind.GENRE, "R&B", "R&B", listOf(
        "r&b", "rnb", "r and b", "r&b & soul", "soul", "neo-soul", "neo soul", "funk", "contemporary r&b"
    )),
    METAL("metal", TagKind.GENRE, "金屬", "Metal", listOf(
        "metal", "heavy metal", "metalcore", "death metal", "black metal", "nu metal", "thrash metal"
    )),
    INDIE("indie", TagKind.GENRE, "獨立", "Indie", listOf(
        "indie", "indie & alternative", "alternative", "indie pop", "indie rock", "indie folk",
        "shoegaze", "dream pop", "bedroom pop", "獨立"
    )),
    ROCK("rock", TagKind.GENRE, "搖滾", "Rock", listOf(
        "rock", "alternative rock", "hard rock", "classic rock", "punk", "pop punk", "punk rock",
        "post-rock", "rock & roll", "rock and roll", "grunge", "搖滾"
    )),
    ELECTRONIC("electronic", TagKind.GENRE, "電子", "Electronic", listOf(
        "electronic", "dance & electronic", "edm", "house", "techno", "trance", "dubstep",
        "drum and bass", "dnb", "electro", "future bass", "dance", "electronica", "電子"
    )),
    JAZZ("jazz", TagKind.GENRE, "爵士", "Jazz", listOf(
        "jazz", "smooth jazz", "bossa nova", "swing", "vocal jazz", "爵士"
    )),
    CLASSICAL("classical", TagKind.GENRE, "古典", "Classical", listOf(
        "classical", "piano", "orchestral", "instrumental", "neoclassical", "neo-classical", "古典", "鋼琴"
    )),
    FOLK("folk", TagKind.GENRE, "民謠", "Folk", listOf(
        "folk", "folk & acoustic", "acoustic", "singer-songwriter", "country", "country & americana",
        "americana", "bluegrass", "民謠"
    )),
    SOUNDTRACK("soundtrack", TagKind.GENRE, "原聲帶", "Soundtrack", listOf(
        "soundtrack", "soundtracks", "soundtracks & musicals", "ost", "film score", "score",
        "musical", "musicals", "game music", "video game music", "原聲帶"
    )),
    BALLAD("ballad", TagKind.GENRE, "抒情", "Ballad", listOf(
        "ballad", "ballads", "power ballad", "抒情"
    )),
    LATIN("latin", TagKind.GENRE, "拉丁", "Latin", listOf(
        "latin", "reggaeton", "latin pop", "salsa", "bachata", "latin music"
    )),
    REGGAE("reggae", TagKind.GENRE, "雷鬼", "Reggae", listOf(
        "reggae", "reggae & caribbean", "dancehall", "ska"
    )),
    BLUES("blues", TagKind.GENRE, "藍調", "Blues", listOf(
        "blues", "blues rock", "藍調"
    )),
    POP("pop", TagKind.GENRE, "流行", "Pop", listOf(
        "pop", "pop music", "dance pop", "synthpop", "synth-pop", "electropop", "teen pop", "流行"
    )),

    // ---- 心情 ----
    CHILL("chill", TagKind.MOOD, "放鬆", "Chill", listOf(
        "chill", "chillout", "chill out", "relax", "relaxing", "calm", "mellow", "easy listening",
        "ambient", "放鬆"
    )),
    ENERGY("energy", TagKind.MOOD, "提神", "Energy", listOf(
        "energy boosters", "energy booster", "energetic", "energy", "upbeat", "hype", "提神"
    )),
    FEEL_GOOD("feelgood", TagKind.MOOD, "心情好", "Feel good", listOf(
        "feel good", "feel-good", "feelgood", "happy", "uplifting", "fun", "summer", "快樂"
    )),
    FOCUS("focus", TagKind.MOOD, "專注", "Focus", listOf(
        "focus", "study", "studying", "concentration", "reading", "專注", "讀書"
    )),
    PARTY("party", TagKind.MOOD, "派對", "Party", listOf(
        "party", "club", "dance party", "派對"
    )),
    ROMANCE("romance", TagKind.MOOD, "浪漫", "Romance", listOf(
        "romance", "romantic", "love", "love songs", "love song", "wedding", "浪漫", "情歌"
    )),
    SAD("sad", TagKind.MOOD, "傷心", "Sad", listOf(
        "sad", "melancholy", "melancholic", "heartbreak", "breakup", "emotional", "depressing", "傷心", "悲傷"
    )),
    SLEEP("sleep", TagKind.MOOD, "睡眠", "Sleep", listOf(
        "sleep", "sleeping", "lullaby", "bedtime", "睡眠", "睡前"
    )),
    WORKOUT("workout", TagKind.MOOD, "運動", "Workout", listOf(
        "workout", "gym", "running", "fitness", "training", "運動", "健身"
    )),
    COMMUTE("commute", TagKind.MOOD, "通勤", "Commute", listOf(
        "commute", "driving", "road trip", "drive", "travel", "通勤", "開車"
    ));

    val aliases: List<String> = aliases

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: String): Tag? = byId[id]
    }
}

/** 標準化後的單一標籤，weight 介於 0～1。 */
data class WeightedTag(val tag: Tag, val weight: Float)

/**
 * 每首歌最多保留 [maxGenres] 個曲風、[maxMoods] 個心情，取權重最高的；
 * 同一個標籤出現多次時取最大權重。
 */
fun List<WeightedTag>.pickTop(maxGenres: Int = 2, maxMoods: Int = 2): List<WeightedTag> {
    val best = groupBy { it.tag }.map { (tag, list) -> WeightedTag(tag, list.maxOf { it.weight }) }
    return best.filter { it.tag.kind == TagKind.GENRE }.sortedByDescending { it.weight }.take(maxGenres) +
        best.filter { it.tag.kind == TagKind.MOOD }.sortedByDescending { it.weight }.take(maxMoods)
}
