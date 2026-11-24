package app.vitune.providers.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagNormalizerTest {
    private fun n(s: String) = TagNormalizer.normalize(s)

    @Test
    fun youtubeMusicGenres() {
        // YouTube Music「Moods & genres」頁的英文分類名稱（hl=en）
        val expected = mapOf(
            "Pop" to Tag.POP,
            "Hip-Hop" to Tag.HIPHOP,
            "R&B & Soul" to Tag.RNB,
            "Rock" to Tag.ROCK,
            "Dance & Electronic" to Tag.ELECTRONIC,
            "Jazz" to Tag.JAZZ,
            "Classical" to Tag.CLASSICAL,
            "Country & Americana" to Tag.FOLK,
            "Folk & Acoustic" to Tag.FOLK,
            "Indie & Alternative" to Tag.INDIE,
            "Metal" to Tag.METAL,
            "Latin" to Tag.LATIN,
            "K-Pop" to Tag.KPOP,
            "J-Pop" to Tag.JPOP,
            "Mandopop & Cantopop" to Tag.CPOP,
            "Reggae & Caribbean" to Tag.REGGAE,
            "Blues" to Tag.BLUES,
            "Soundtracks & Musicals" to Tag.SOUNDTRACK
        )
        expected.forEach { (raw, tag) -> assertEquals(tag, n(raw), raw) }
    }

    @Test
    fun youtubeMusicMoods() {
        val expected = mapOf(
            "Chill" to Tag.CHILL,
            "Commute" to Tag.COMMUTE,
            "Energy Boosters" to Tag.ENERGY,
            "Feel Good" to Tag.FEEL_GOOD,
            "Focus" to Tag.FOCUS,
            "Party" to Tag.PARTY,
            "Romance" to Tag.ROMANCE,
            "Sad" to Tag.SAD,
            "Sleep" to Tag.SLEEP,
            "Workout" to Tag.WORKOUT
        )
        expected.forEach { (raw, tag) -> assertEquals(tag, n(raw), raw) }
    }

    @Test
    fun lastFmTags() {
        assertEquals(Tag.CPOP, n("mandopop"))
        assertEquals(Tag.CPOP, n("Chinese"))
        assertEquals(Tag.JPOP, n("j-rock"))          // 不能被當成 rock
        assertEquals(Tag.LOFI, n("lofi hip hop"))    // 不能被當成 hip hop
        assertEquals(Tag.INDIE, n("indie pop"))      // 不能被當成 pop
        assertEquals(Tag.RNB, n("rnb"))
        assertEquals(Tag.HIPHOP, n("rap"))
        assertEquals(Tag.ELECTRONIC, n("EDM"))
        assertEquals(Tag.CHILL, n("chillout"))
        assertEquals(Tag.SAD, n("melancholic"))
        assertEquals(Tag.ROMANCE, n("love songs"))
    }

    @Test
    fun chineseTags() {
        assertEquals(Tag.CPOP, n("華語"))
        assertEquals(Tag.BALLAD, n("抒情"))
        assertEquals(Tag.ROCK, n("搖滾"))
        assertEquals(Tag.ROMANCE, n("情歌"))
    }

    @Test
    fun noiseIsIgnored() {
        assertNull(n(""))
        assertNull(n("   "))
        assertNull(n("seen live"))
        assertNull(n("favorites"))
        assertNull(n("1980s"))
        assertNull(n("90s"))
        assertNull(n("funky"))      // 不能因為含 fun 被當成心情好
    }

    @Test
    fun pickTopKeepsBestPerKind() {
        val tags = listOf(
            WeightedTag(Tag.POP, 0.5f), WeightedTag(Tag.POP, 0.9f), WeightedTag(Tag.ROCK, 0.7f),
            WeightedTag(Tag.JAZZ, 0.2f), WeightedTag(Tag.SAD, 0.6f), WeightedTag(Tag.CHILL, 0.3f),
            WeightedTag(Tag.SLEEP, 0.1f)
        ).pickTop()
        assertEquals(listOf(Tag.POP, Tag.ROCK, Tag.SAD, Tag.CHILL), tags.map { it.tag })
        assertEquals(0.9f, tags.first().weight)
    }

    @Test
    fun idsAreUniqueAndRoundTrip() {
        assertEquals(Tag.entries.size, Tag.entries.map { it.id }.toSet().size)
        Tag.entries.forEach { assertEquals(it, Tag.fromId(it.id)) }
    }
}
