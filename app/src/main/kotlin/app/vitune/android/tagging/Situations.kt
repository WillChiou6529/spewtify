package app.vitune.android.tagging

import app.vitune.android.Database
import app.vitune.android.models.Song
import app.vitune.android.models.SongTag
import app.vitune.providers.tags.Situation
import app.vitune.providers.tags.Tag
import app.vitune.providers.tags.WeightedTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** 情境（讀書／運動／睡前／開車）：由每首收藏的標籤列即時算出，手動設定優先。 */
object Situations {
    /** 一首歌的標籤列 → 它屬於哪些情境。 */
    fun of(rows: List<SongTag>): Set<Situation> {
        val tags = rows.filter { it.weight > 0 }.mapNotNull { r -> Tag.fromId(r.tag)?.let { WeightedTag(it, r.weight) } }
        val manual = rows.mapNotNull { r -> Situation.fromTagId(r.tag)?.let { it to (r.weight > 0) } }.toMap()
        return Situation.of(tags, manual)
    }

    fun group(rows: List<SongTag>): Map<String, Set<Situation>> =
        rows.groupBy { it.songId }.mapValues { (_, list) -> of(list) }

    fun counts(rows: List<SongTag>): Map<Situation, Int> {
        val grouped = group(rows)
        return Situation.entries.associateWith { s -> grouped.count { s in it.value } }
    }

    suspend fun songsFor(situation: Situation): List<Song> = withContext(Dispatchers.IO) {
        val ids = group(Database.likedSongTagsNow()).filterValues { situation in it }.keys
        Database.likedSongsNow().filter { it.id in ids }
    }
}

fun Situation.displayName(): String = if (Locale.getDefault().language == "zh") zhName else enName
