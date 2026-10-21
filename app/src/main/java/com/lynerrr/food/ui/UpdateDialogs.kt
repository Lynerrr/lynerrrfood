package com.lynerrr.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lynerrr.food.BuildConfig
import com.lynerrr.food.R
import com.lynerrr.food.data.AppUpdater
import java.util.Locale

/** Update prompt, download progress and install problems, shown above whatever screen is open. */
@Composable
fun UpdateDialogs(updater: AppUpdater) {
    val state by updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    when (val s = state) {
        AppUpdater.State.Idle, AppUpdater.State.Installing -> Unit
        is AppUpdater.State.Available -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.update_available, s.release.version, BuildConfig.VERSION_NAME))
                    if (s.release.notes.isNotEmpty()) {
                        Text(
                            s.release.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { updater.updateNow(s.release) }) { Text(stringResource(R.string.update_now)) } },
            dismissButton = { TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.later)) } },
        )
        is AppUpdater.State.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading, s.release.version)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.update_progress, megabytes(s.done), megabytes(s.total)))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is AppUpdater.State.NeedsPermission -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission)) },
            confirmButton = {
                TextButton(onClick = { context.startActivity(updater.permissionSettingsIntent()) }) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = { TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.later)) } },
        )
        is AppUpdater.State.Failed -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text(stringResource(R.string.update_failed)) },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.ok)) } },
        )
    }
}

private fun megabytes(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / 1_048_576.0)
