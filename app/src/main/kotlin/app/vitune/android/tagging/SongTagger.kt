package app.vitune.android.tagging

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.vitune.android.Database
import app.vitune.android.DatabaseDependency
import app.vitune.android.models.Song
import app.vitune.android.models.SongTag
import app.vitune.android.models.SongTagState
import app.vitune.android.preferences.DataPreferences
import app.vitune.providers.tags.LastFm
import app.vitune.providers.tags.Tag
import app.vitune.providers.tags.TagKind
import app.vitune.providers.tags.TitleCleaner
import app.vitune.providers.tags.WeightedTag
import app.vitune.providers.tags.pickTop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days

private const val TAG = "SongTagger"

/**
 * 幫「我的收藏」自動貼曲風／心情標籤。
 * 順序：官方歌單直接收錄 → 依歌手在官方歌單的分布推估 → 還缺曲風或心情才問 Last.fm（有填 key 才會用）。
 */
object SongTagger {
    private const val BATCH = 50
    private const val MAX_PER_RUN = 500
    private const val ARTIST_MIN_HITS = 2
    private const val ARTIST_MIN_SHARE = 0.2f
    private const val LASTFM_DELAY_MS = 250L
    private val RETAG_AFTER = 30.days

    data class Outcome(val processed: Int, val tagged: Int)

    internal data class Candidate(val tag: WeightedTag, val source: String)

    suspend fun run(context: Context, force: Boolean): Outcome? = withContext(Dispatchers.IO) {
        if (force) Database.resetTagStates()

        val index = YtTagIndex.load(context, forceRefresh = force)
        val lastFmKey = DataPreferences.lastFmApiKey.trim()
        if (index == null && lastFmKey.isEmpty()) {
            Log.w(TAG, "沒有官方索引也沒有 Last.fm key，稍後重試")
            return@withContext null
        }

        val since = System.currentTimeMillis() - RETAG_AFTER.inWholeMilliseconds
        var processed = 0
        var tagged = 0
        val seen = HashSet<String>()
        while (processed < MAX_PER_RUN) {
            // 沒存狀態的歌下一批還會出現，用 seen 避免同一首重查
            val batch = Database.likedSongsNeedingTags(since, BATCH).filter { seen.add(it.id) }
            if (batch.isEmpty()) break
            batch.forEach { song ->
                val candidates = tagsFor(song, index, lastFmKey)
                processed++
                if (candidates.isNotEmpty()) tagged++
                // 沒有官方索引又查不到時不記狀態，下次索引恢復還會重試
                if (index != null || candidates.isNotEmpty()) save(song, candidates)
            }
        }
        Log.i(TAG, "分類 $processed 首，其中 $tagged 首有標籤")
        Outcome(processed, tagged)
    }

    private suspend fun tagsFor(song: Song, index: YtTagIndex?, lastFmKey: String): List<Candidate> {
        val found = mutableListOf<Candidate>()

        if (index != null) {
            index.songs[song.id].orEmpty().forEach { id ->
                Tag.fromId(id)?.let { found += Candidate(WeightedTag(it, 1f), SongTag.SOURCE_YT) }
            }
            val artistCounts = Database.artistIdsOf(song.id).mapNotNull { index.artists[it] }
                .ifEmpty {
                    listOfNotNull(
                        TitleCleaner.primaryArtist(song.artistsText)?.lowercase()?.let { index.artistNames[it] }
                    )
                }
            found += fromArtistCounts(artistCounts)
        }

        val hasGenre = found.any { it.tag.tag.kind == TagKind.GENRE }
        val hasMood = found.any { it.tag.tag.kind == TagKind.MOOD }
        if (lastFmKey.isNotEmpty() && !(hasGenre && hasMood)) {
            val artist = TitleCleaner.primaryArtist(song.artistsText)
            if (artist != null) {
                LastFm.tagsFor(lastFmKey, artist, TitleCleaner.clean(song.title))
                    ?.onFailure { Log.w(TAG, "Last.fm 查詢失敗：${song.title}", it) }
                    ?.getOrNull()
                    ?.forEach { found += Candidate(it, SongTag.SOURCE_LASTFM) }
                delay(LASTFM_DELAY_MS)   // Last.fm 每秒最多約 5 次
            }
        }

        return pick(found)
    }

    /** 歌手在官方歌單出現 ≥ 2 次、且某分類占 ≥ 20% 才算數；權重 0.4～0.9。 */
    internal fun fromArtistCounts(counts: List<Map<String, Int>>): List<Candidate> {
        val merged = HashMap<String, Int>()
        counts.forEach { map -> map.forEach { (k, v) -> merged[k] = (merged[k] ?: 0) + v } }
        val total = merged.values.sum().toFloat()
        if (total == 0f) return emptyList()
        return merged.mapNotNull { (id, hits) ->
            val share = hits / total
            val tag = Tag.fromId(id) ?: return@mapNotNull null
            if (hits < ARTIST_MIN_HITS || share < ARTIST_MIN_SHARE) null
            else Candidate(WeightedTag(tag, 0.4f + 0.5f * share), SongTag.SOURCE_YT_ARTIST)
        }
    }

    /** 每首最多 2 個曲風＋2 個心情；同標籤多個來源時，來源跟著權重最高的那筆。 */
    internal fun pick(found: List<Candidate>): List<Candidate> {
        val sourceOf = found.groupBy { it.tag.tag }.mapValues { (_, list) -> list.maxBy { it.tag.weight }.source }
        return found.map { it.tag }.pickTop().map { Candidate(it, sourceOf.getValue(it.tag)) }
    }

    private fun save(song: Song, candidates: List<Candidate>) {
        DatabaseDependency.instance.runInTransaction {
            Database.clearAutoTags(song.id)
            Database.insertTags(
                candidates.map { SongTag(song.id, it.tag.tag.id, it.source, it.tag.weight) }
            )
            Database.upsert(
                SongTagState(
                    songId = song.id,
                    taggedAt = System.currentTimeMillis(),
                    sources = candidates.map { it.source }.distinct().joinToString(",")
                )
            )
        }
    }
}

class SongTagWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = runCatching { SongTagger.run(applicationContext, inputData.getBoolean(KEY_FORCE, false)) }
            .onFailure { Log.e(TAG, "自動分類失敗", it) }
            .getOrNull()
        return if (outcome == null) Result.retry() else Result.success()
    }

    companion object {
        private const val KEY_FORCE = "force"
        private const val PERIODIC = "song_tag_periodic"
        private const val NOW = "song_tag_now"

        private val constraints = Constraints(requiredNetworkType = NetworkType.CONNECTED)

        /** App 啟動時呼叫：每天跑一次，並補分類還沒分類過的收藏。 */
        fun schedule(context: Context) = runCatching {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SongTagWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build()
            )
            wm.enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SongTagWorker>().setConstraints(constraints).build()
            )
        }.onFailure { Log.w(TAG, "排程失敗", it) }

        /** 設定頁按鈕：force = true 會清掉狀態、重抓官方索引、全部重新分類（手動標籤保留）。 */
        fun runNow(context: Context, force: Boolean) = runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SongTagWorker>()
                    .setConstraints(constraints)
                    .setInputData(workDataOf(KEY_FORCE to force))
                    .build()
            )
        }.onFailure { Log.w(TAG, "啟動分類失敗", it) }
    }
}
