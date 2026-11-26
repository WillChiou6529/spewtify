package app.vitune.providers.tags

import kotlin.test.Test
import kotlin.test.assertEquals

class TitleCleanerTest {
    @Test
    fun removesVideoNoise() {
        assertEquals("小幸運", TitleCleaner.clean("田馥甄 Hebe Tien - 小幸運 (Official Music Video)").substringAfter("- "))
        assertEquals("Blinding Lights", TitleCleaner.clean("Blinding Lights (Official Audio)"))
        assertEquals("告白氣球", TitleCleaner.clean("告白氣球【MV】"))
        assertEquals("Shape of You", TitleCleaner.clean("Shape of You [Official Lyric Video]"))
        assertEquals("夜曲", TitleCleaner.clean("夜曲（官方完整版MV）"))
    }

    @Test
    fun removesFeaturing() {
        assertEquals("Stay", TitleCleaner.clean("Stay (with Justin Bieber)"))
        assertEquals("Señorita", TitleCleaner.clean("Señorita feat. Camila Cabello"))
    }

    @Test
    fun keepsRealTitle() {
        assertEquals("Dancing With Myself", TitleCleaner.clean("Dancing With Myself"))
        assertEquals("Love (Remix)", TitleCleaner.clean("Love (Remix)"))
        assertEquals("(Official)", TitleCleaner.clean("(Official)"))   // 清完變空字串時保留原名
    }

    @Test
    fun primaryArtist() {
        assertEquals("周杰倫", TitleCleaner.primaryArtist("周杰倫, 費玉清"))
        assertEquals("Ed Sheeran", TitleCleaner.primaryArtist("Ed Sheeran & Justin Bieber"))
        assertEquals("五月天", TitleCleaner.primaryArtist("五月天 - Topic"))
        assertEquals(null, TitleCleaner.primaryArtist(null))
        // 歌手名裡含 ft／feat／x 的字母不能被切開
        assertEquals("Taylor Swift", TitleCleaner.primaryArtist("Taylor Swift"))
        assertEquals("Daft Punk", TitleCleaner.primaryArtist("Daft Punk feat. Pharrell Williams"))
        assertEquals("Lefty Gunplay", TitleCleaner.primaryArtist("Lefty Gunplay ft. Someone"))
        assertEquals("Alex Warren", TitleCleaner.primaryArtist("Alex Warren x ROSÉ"))
        assertEquals("Maxx", TitleCleaner.primaryArtist("Maxx"))
    }
}
