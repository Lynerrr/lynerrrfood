package com.lynerrr.food.data

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.IntentCompat
import com.lynerrr.food.BuildConfig
import com.lynerrr.food.FoodApp
import com.lynerrr.food.R
import com.lynerrr.food.net.GitHubReleases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Self-update from GitHub releases: a quiet check whenever the app comes to the foreground (Android usually keeps it
 * running in the background, so "on start" alone would rarely happen), then, when the user agrees, download the APK
 * with the system's DownloadManager (it keeps going when the app is left, retries and resumes after network drops)
 * and hand it to the system installer. Updates install only if signed with the same key as the installed app.
 */
class AppUpdater(private val context: Context, private val repo: String, private val scope: CoroutineScope) {
    sealed interface State {
        data object Idle : State
        data class Available(val release: GitHubReleases.Release) : State
        /** [waiting]: paused by the system, e.g. until the network is back. */
        data class Downloading(
            val release: GitHubReleases.Release,
            val done: Long,
            val total: Long,
            val waiting: Boolean = false,
        ) : State
        /** Downloaded while the app was in the background; installs when it comes back. */
        data class Downloaded(val release: GitHubReleases.Release, val apk: File) : State
        /** Downloaded, but the user still has to allow this app to install apps. */
        data class NeedsPermission(val release: GitHubReleases.Release, val apk: File) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    /** Outcome of a check the user started from Settings (automatic checks stay silent). */
    sealed interface ManualCheck {
        data object Checking : ManualCheck
        data object UpToDate : ManualCheck
        data class Failed(val message: String) : ManualCheck
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _manualCheck = MutableStateFlow<ManualCheck?>(null)
    val manualCheck: StateFlow<ManualCheck?> = _manualCheck.asStateFlow()

    /** Whether this build knows where to look for updates. */
    val isConfigured get() = repo.isNotEmpty()
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    /** When the last check started (elapsed time, so clock changes don't matter); 0 = not yet in this process. */
    private var lastCheck = 0L
    private var checkJob: Job? = null
    private var job: Job? = null
    private var inForeground = false
    private val downloads = context.getSystemService(DownloadManager::class.java)

    /** Looks for a newer release, at most once per [CHECK_INTERVAL_MS]; failures (offline, rate limit) are ignored. */
    private fun checkIfDue() {
        if (repo.isEmpty() || _state.value != State.Idle || checkJob?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (lastCheck != 0L && now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now
        checkJob = scope.launch(Dispatchers.IO) {
            val release = try {
                GitHubReleases.latest(repo, BuildConfig.UPDATES_API)
            } catch (e: Exception) {
                null
            } ?: return@launch
            if (GitHubReleases.isNewer(release.version, BuildConfig.VERSION_NAME) && !isSnoozed(release.version) &&
                _state.value == State.Idle
            ) {
                _state.value = State.Available(release)
            }
        }
    }

    /** Checks right away for the Settings button, ignoring the interval and "Later"; reports the outcome. */
    fun checkNow() {
        if (repo.isEmpty() || checkJob?.isActive == true || _state.value != State.Idle) return
        lastCheck = SystemClock.elapsedRealtime()
        _manualCheck.value = ManualCheck.Checking
        checkJob = scope.launch(Dispatchers.IO) {
            _manualCheck.value = try {
                val release = GitHubReleases.latest(repo, BuildConfig.UPDATES_API)
                if (release != null && GitHubReleases.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    _state.value = State.Available(release)
                    null
                } else {
                    ManualCheck.UpToDate
                }
            } catch (e: Exception) {
                ManualCheck.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** "Later": hides this version's prompt for [SNOOZE_MS]; a newer release still prompts right away. */
    fun later(release: GitHubReleases.Release) {
        prefs.edit()
            .putString(KEY_SNOOZED_VERSION, release.version)
            .putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
            .apply()
        dismiss()
    }

    private fun isSnoozed(version: String) =
        prefs.getString(KEY_SNOOZED_VERSION, null) == version && System.currentTimeMillis() < prefs.getLong(KEY_SNOOZED_UNTIL, 0)

    fun dismiss() {
        job?.cancel()
        forgetDownload()
        _state.value = State.Idle
    }

    fun updateNow(release: GitHubReleases.Release) {
        forgetDownload() // an earlier, unfinished one
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return run { _state.value = State.Failed("No storage available for the download") }
        File(dir, APK_NAME).delete()
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle(context.getString(R.string.update_notification, release.version))
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val id = downloads.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_DOWNLOAD_VERSION, release.version).apply()
        watch(id, release)
    }

    /** The system screen where the user allows this app to install updates. */
    fun permissionSettingsIntent() =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * The app came to the foreground: install an update that finished downloading meanwhile (or that waited for the
     * user to allow installs), pick up a download still running in the system, otherwise check for a new release
     * when one is due.
     */
    fun onResume() {
        inForeground = true
        when (val s = _state.value) {
            is State.Downloaded -> installOrAsk(s.release, s.apk)
            is State.NeedsPermission -> if (context.packageManager.canRequestPackageInstalls()) installOrAsk(s.release, s.apk)
            State.Idle -> if (!reattachDownload()) checkIfDue()
            else -> Unit
        }
    }

    /** Android doesn't let apps open the installer from the background, so finished downloads wait for [onResume]. */
    fun onPause() {
        inForeground = false
    }

    /** After the app was closed: follows a download it started earlier, or cleans up one already installed. */
    private fun reattachDownload(): Boolean {
        if (job?.isActive == true) return true
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1)
        val version = prefs.getString(KEY_DOWNLOAD_VERSION, null)
        if (id < 0 || version == null) return false
        if (!GitHubReleases.isNewer(version, BuildConfig.VERSION_NAME)) {
            forgetDownload()
            return false
        }
        watch(id, GitHubReleases.Release(version, "", "", 0))
        return true
    }

    /** Follows a DownloadManager download until it ends. The download itself runs in the system, app open or not. */
    private fun watch(id: Long, release: GitHubReleases.Release) {
        job?.cancel()
        _state.value = State.Downloading(release, 0, release.apkSize)
        job = scope.launch(Dispatchers.IO) {
            while (true) {
                val row = downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    fun long(column: String) = c.getLong(c.getColumnIndexOrThrow(column))
                    DownloadRow(
                        status = long(DownloadManager.COLUMN_STATUS).toInt(),
                        reason = long(DownloadManager.COLUMN_REASON).toInt(),
                        done = long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                        total = long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                        localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)),
                    )
                }
                when (row?.status) {
                    null -> { // removed, e.g. cancelled from the notification
                        forgetDownload()
                        _state.value = State.Idle
                        return@launch
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val apk = File(Uri.parse(row.localUri).path!!)
                        if (inForeground) installOrAsk(release, apk) else _state.value = State.Downloaded(release, apk)
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        forgetDownload()
                        _state.value = State.Failed(downloadError(row.reason))
                        return@launch
                    }
                    else -> _state.value = State.Downloading(
                        release,
                        row.done,
                        row.total.takeIf { it > 0 } ?: release.apkSize,
                        waiting = row.status == DownloadManager.STATUS_PAUSED,
                    )
                }
                delay(POLL_MS)
            }
        }
    }

    private class DownloadRow(val status: Int, val reason: Int, val done: Long, val total: Long, val localUri: String?)

    private fun downloadError(reason: Int) = when (reason) {
        in 400..599 -> "HTTP $reason"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage"
        DownloadManager.ERROR_CANNOT_RESUME -> "The download couldn't be resumed"
        else -> "Download error $reason"
    }

    /** Drops the remembered download; removing it from DownloadManager also deletes its file. */
    private fun forgetDownload() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1)
        if (id >= 0) downloads.remove(id)
        prefs.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_DOWNLOAD_VERSION).apply()
    }

    private fun installOrAsk(release: GitHubReleases.Release, apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = State.NeedsPermission(release, apk)
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                install(apk)
            } catch (e: Exception) {
                forgetDownload()
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun install(apkFile: File) {
        _state.value = State.Installing
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apkFile.length())
            // Once this app installed itself, Android 12+ can skip the confirmation for later updates.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update.apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(context, 0, Intent(context, UpdateStatusReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    /** Result of an install session; on success Android replaces and restarts the app. */
    internal fun onInstallStatus(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                forgetDownload()
                _state.value = State.Idle
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> { // cancelled in the system dialog
                forgetDownload()
                _state.value = State.Idle
            }
            else -> {
                forgetDownload()
                _state.value = State.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed")
            }
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
        const val SNOOZE_MS = 24 * 60 * 60 * 1000L
        const val KEY_SNOOZED_VERSION = "snoozed_version"
        const val KEY_SNOOZED_UNTIL = "snoozed_until"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_DOWNLOAD_VERSION = "download_version"
        const val APK_NAME = "update.apk"
        const val POLL_MS = 500L
    }
}

/** Receives install session results (declared in the manifest, not exported). */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as FoodApp).updater.onInstallStatus(context, intent)
    }
}
