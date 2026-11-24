package app.vitune.providers.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SituationTest {
    private fun tags(vararg t: Pair<Tag, Float>) = t.map { WeightedTag(it.first, it.second) }

    @Test
    fun lofiChillIsStudyAndSleepNotWorkout() {
        val s = Situation.of(tags(Tag.LOFI to 1f, Tag.CHILL to 0.8f))
        assertTrue(Situation.STUDY in s)
        assertTrue(Situation.SLEEP in s)
        assertFalse(Situation.WORKOUT in s)
    }

    @Test
    fun edmPartyIsWorkoutNotSleepOrStudy() {
        val s = Situation.of(tags(Tag.ELECTRONIC to 1f, Tag.PARTY to 0.9f))
        assertTrue(Situation.WORKOUT in s)
        assertFalse(Situation.SLEEP in s)
        assertFalse(Situation.STUDY in s)
    }

    @Test
    fun feelGoodPopIsDrive() {
        assertTrue(Situation.DRIVE in Situation.of(tags(Tag.POP to 1f, Tag.FEEL_GOOD to 0.6f)))
        assertTrue(Situation.DRIVE in Situation.of(tags(Tag.CPOP to 1f)))
    }

    @Test
    fun metalNeverForSleepOrStudy() {
        val s = Situation.of(tags(Tag.METAL to 1f, Tag.CHILL to 0.5f))
        assertFalse(Situation.SLEEP in s)
        assertFalse(Situation.STUDY in s)
    }

    @Test
    fun weakArtistGuessIsNotEnough() {
        // 只靠歌手推估、權重低的單一標籤，不該硬塞進情境
        assertTrue(Situation.of(tags(Tag.JAZZ to 0.4f)).isEmpty())
    }

    @Test
    fun manualOverridesAuto() {
        val auto = tags(Tag.LOFI to 1f)
        assertFalse(Situation.STUDY in Situation.of(auto, mapOf(Situation.STUDY to false)))
        assertTrue(Situation.WORKOUT in Situation.of(auto, mapOf(Situation.WORKOUT to true)))
    }

    @Test
    fun tagIdsRoundTripAndDoNotCollideWithTags() {
        Situation.entries.forEach {
            assertEquals(it, Situation.fromTagId(it.tagId))
            assertNull(Tag.fromId(it.tagId))
        }
        assertNull(Situation.fromTagId("study"))
    }
}
