package app.vitune.android.tagging

import kotlinx.coroutines.CancellationException

/**
 * 像 runCatching，但「取消」一定往外丟：離開畫面或 Worker 被停時，不會被當成一般失敗吞掉，
 * 呼叫端也不會拿舊資料或回 retry 假裝沒事。
 */
internal inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
