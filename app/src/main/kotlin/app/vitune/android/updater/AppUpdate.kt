package app.vitune.android.updater

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.vitune.android.BuildConfig
import app.vitune.core.data.utils.version
import app.vitune.providers.github.GitHub
import app.vitune.providers.github.models.Release
import app.vitune.providers.github.requests.releases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "AppUpdate"

/**
 * Spewtify App 內更新：查 GitHub Releases 有沒有比手上新的版本 → 使用者按「立即更新」→ 下載 APK → 開系統安裝畫面。
 * 新舊版用同一把簽章金鑰，所以會直接覆蓋安裝，收藏與設定都保留。
 */
object AppUpdate {
    const val REPO_OWNER = "WillChiou6529"
    const val REPO_NAME = "spewtify"
    private const val APK_TYPE = "application/vnd.android.package-archive"
    private const val DIR = "update"

    /** 手上的版本（去掉 -RELEASE 之類的尾巴），例如 1.0.23 */
    val currentVersion: String = BuildConfig.VERSION_NAME.substringBeforeLast("-")

    /** 比手上新、而且已經有 APK 的最新正式版；沒有新版回 success(null)，網路失敗回 failure。 */
    suspend fun newerRelease(): Result<Release?> = withContext(Dispatchers.IO) {
        val mine = currentVersion.version
        GitHub.releases(owner = REPO_OWNER, repo = REPO_NAME)
            ?.mapCatching { releases ->
                releases
                    .filter { !it.draft && !it.preRelease && apkOf(it) != null }
                    .filter { it.tag.version > mine }
                    .maxWithOrNull { a, b -> a.tag.version.compareTo(b.tag.version) }
            }
            ?: Result.failure(IllegalStateException("沒有回應"))
    }

    fun apkOf(release: Release) = release.assets.firstOrNull {
        it.state == Release.Asset.State.Uploaded && (it.contentType == APK_TYPE || it.name.endsWith(".apk"))
    }

    /** 發佈說明：只留 commit 說明那段，拿掉固定的下載網址那行。 */
    fun notesOf(release: Release): String = release.markdown.orEmpty()
        .lines()
        .filterNot { "releases/latest/download" in it }
        .joinToString("\n")
        .trim()

    /** 下載到 cache/update/Spewtify.apk；onProgress 0~1（不知道大小時不呼叫）。 */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val asset = apkOf(release) ?: error("這一版沒有 APK")
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val part = File(dir, "Spewtify.apk.part")
            val file = File(dir, "Spewtify.apk")

            val connection = URL(asset.downloadUrl.toString()).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (total > 0 && part.length() != total) error("下載不完整：${part.length()}/$total")
            } finally {
                connection.disconnect()
            }
            if (!part.renameTo(file)) error("存檔失敗")
            Log.i(TAG, "下載完成 ${release.tag}：${file.length()} bytes")
            file
        }

    /** Android 8 以上要使用者允許「安裝不明應用程式」才能開安裝畫面。 */
    fun canInstall(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
