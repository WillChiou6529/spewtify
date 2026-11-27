package app.vitune.android.updater

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.ui.components.themed.CircularProgressIndicator
import app.vitune.android.ui.components.themed.DefaultDialog
import app.vitune.android.ui.components.themed.DialogTextButton
import app.vitune.android.utils.bold
import app.vitune.android.utils.center
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.providers.github.models.Release
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.hours

private sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val progress: Float?) : UpdateState
    data object NeedPermission : UpdateState
    data object Failed : UpdateState
    data object Installing : UpdateState
}

/** 有新版時的對話框：稍後／立即更新 → 下載（顯示百分比）→ 開系統安裝畫面。 */
@Composable
fun UpdateDialog(release: Release, onDismiss: () -> Unit) = DefaultDialog(onDismiss = onDismiss) {
    UpdateDialogBody(release = release, onDismiss = onDismiss)
}

@Composable
fun ColumnScope.UpdateDialogBody(release: Release, onDismiss: () -> Unit) {
    val (_, typography) = LocalAppearance.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(release.tag) { mutableStateOf<UpdateState>(UpdateState.Idle) }
    // 下載好的檔留著：去設定頁開權限回來再按一次，不用重下載
    var downloaded by remember(release.tag) { mutableStateOf<File?>(null) }

    fun installOrAskPermission(apk: File) {
        if (!AppUpdate.canInstall(context)) {
            state = UpdateState.NeedPermission
            AppUpdate.openInstallPermissionSettings(context)
            return
        }
        state = UpdateState.Installing
        runCatching { AppUpdate.install(context, apk) }.onFailure { state = UpdateState.Failed }
    }

    fun start() {
        downloaded?.takeIf { it.exists() }?.let { return installOrAskPermission(it) }
        state = UpdateState.Downloading(null)
        scope.launch {
            val apk = try {
                AppUpdate.download(context.applicationContext, release) { state = UpdateState.Downloading(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                state = UpdateState.Failed
                return@launch
            }
            downloaded = apk
            installOrAskPermission(apk)
        }
    }

    BasicText(
        text = stringResource(R.string.new_version_available),
        style = typography.xs.semiBold.center
    )
    Spacer(modifier = Modifier.height(8.dp))
    BasicText(
        text = release.name ?: release.tag,
        style = typography.m.bold.center
    )
    BasicText(
        text = stringResource(R.string.current_version, AppUpdate.currentVersion),
        style = typography.xxs.secondary.center
    )

    AppUpdate.notesOf(release).takeIf { it.isNotEmpty() }?.let { notes ->
        BasicText(
            text = notes,
            style = typography.xs.secondary,
            modifier = Modifier
                .padding(top = 12.dp)
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState())
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    when (val s = state) {
        is UpdateState.Downloading -> {
            CircularProgressIndicator(
                progress = s.progress,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.CenterHorizontally)
            )
            BasicText(
                text = s.progress?.let { stringResource(R.string.update_downloading, (it * 100).toInt()) }
                    ?: stringResource(R.string.update_connecting),
                style = typography.xs.center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
            )
        }

        else -> {
            val message = when (s) {
                UpdateState.NeedPermission -> stringResource(R.string.update_need_permission)
                UpdateState.Failed -> stringResource(R.string.update_failed)
                UpdateState.Installing -> stringResource(R.string.update_installing)
                else -> null
            }
            message?.let {
                BasicText(
                    text = it,
                    style = typography.xs.semiBold.center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                DialogTextButton(text = stringResource(R.string.update_later), onClick = onDismiss)
                DialogTextButton(
                    text = stringResource(
                        if (s == UpdateState.Idle) R.string.update_now else R.string.retry
                    ),
                    primary = true,
                    onClick = ::start
                )
            }
        }
    }
}

/**
 * 開 App 時自動檢查（最多每 6 小時一次）：有新版就跳出更新對話框，按「立即更新」就下載安裝。
 * 放在主畫面最外層。
 */
@Composable
fun AutoUpdateCheck() {
    val context = LocalContext.current
    var release by remember { mutableStateOf<Release?>(null) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("spewtify_update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_check", 0L) < 6.hours.inWholeMilliseconds) return@LaunchedEffect
        val result = AppUpdate.newerRelease()
        // 網路失敗不記時間，下次開 App 再查
        if (result.isSuccess) prefs.edit().putLong("last_check", now).apply()
        release = result.getOrNull()
    }

    release?.let { UpdateDialog(release = it, onDismiss = { release = null }) }
}
