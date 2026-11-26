package app.vitune.android.charts

import android.util.Log
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.requests.ChartsPage
import app.vitune.providers.innertube.requests.charts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.hours

private const val TAG = "Charts"

/** 排行榜地區（YouTube Music 排行榜的國家代碼；全球是 ZZ）。 */
enum class ChartRegion(val code: String, val zhName: String, val enName: String) {
    TW("TW", "台灣", "Taiwan"),
    GLOBAL("ZZ", "全球", "Global"),
    KR("KR", "南韓", "South Korea"),
    JP("JP", "日本", "Japan"),
    US("US", "美國", "USA");

    fun displayName(): String = if (Locale.getDefault().language == "zh") zhName else enName

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: TW
    }
}

/** 首頁／搜尋頁的一張排行榜卡：headline＝卡片上的大字（Top 100、今日熱播…）。 */
data class ChartCard(
    val playlist: Innertube.PlaylistItem,
    val region: ChartRegion,
    val headline: String
)

object Charts {
    private val TTL = 1.hours
    private val mutex = Mutex()
    private val cache = HashMap<ChartRegion, Pair<Long, ChartsPage>>()

    /** 某地區的排行榜（記憶體快取 1 小時）；失敗回 null。 */
    suspend fun page(region: ChartRegion): ChartsPage? = withContext(Dispatchers.IO) {
        mutex.withLock { cache[region] }
            ?.takeIf { System.currentTimeMillis() - it.first < TTL.inWholeMilliseconds }
            ?.let { return@withContext it.second }

        val page = Innertube.charts(region.code)?.onFailure { Log.w(TAG, "排行榜 ${region.code} 失敗", it) }?.getOrNull()
            ?: return@withContext mutex.withLock { cache[region]?.second }
        mutex.withLock { cache[region] = System.currentTimeMillis() to page }
        page
    }

    /** 首頁「排行榜」：台灣、全球全部榜單＋南韓、日本、美國的百大熱門。 */
    suspend fun homeCards(): List<ChartCard> = coroutineScope {
        ChartRegion.entries.map { region -> async { region to page(region) } }.awaitAll()
            .flatMap { (region, page) ->
                val lists = page?.playlists.orEmpty()
                val picked = when (region) {
                    ChartRegion.TW, ChartRegion.GLOBAL -> lists.filterNot { isLive(it) }
                    else -> listOfNotNull(lists.lastOrNull())
                }
                picked.map { ChartCard(it, region, headline(it.info?.name)) }
            }
            .sortedBy { order(it) }
    }

    /** 搜尋頁：某地區所有榜單（含類型榜）。 */
    fun cardsOf(region: ChartRegion, page: ChartsPage): List<ChartCard> =
        (page.playlists + page.genrePlaylists).map { ChartCard(it, region, headline(it.info?.name)) }

    private fun isLive(p: Innertube.PlaylistItem) = p.info?.name.orEmpty().let { "現場" in it || "Live" in it || "live" in it }

    /** 台灣百大先、台灣本日、全球…；同地區百大優先。 */
    private fun order(card: ChartCard): Int {
        val h = card.headline
        val kind = when {
            h.startsWith("Top 100") -> 0
            h == TODAY_ZH || h == TODAY_EN -> 1
            else -> 2
        }
        return card.region.ordinal * 10 + kind
    }

    private const val TODAY_ZH = "今日熱播"
    private const val TODAY_EN = "Today"

    /** 從榜單名稱抓卡片大字：「台灣百大熱門音樂影片」→ Top 100、「本日熱播音樂影片」→ 今日熱播。 */
    fun headline(name: String?): String {
        val n = name.orEmpty()
        val zh = Locale.getDefault().language == "zh"
        return when {
            "現場" in n || "Live" in n -> "Live Top 100"
            "本日" in n || "Daily" in n || "today" in n.lowercase() -> if (zh) TODAY_ZH else TODAY_EN
            "前 50" in n || "Top 50" in n -> "Top 50"
            "20" in n -> "Top 20"
            "百大" in n || "100" in n -> "Top 100"
            else -> n
        }
    }
}
