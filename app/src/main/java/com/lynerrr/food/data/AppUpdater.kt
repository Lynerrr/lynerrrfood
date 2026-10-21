package com.lynerrr.food.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.IntentCompat
import com.lynerrr.food.BuildConfig
import com.lynerrr.food.FoodApp
import com.lynerrr.food.net.GitHubReleases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from GitHub releases: a quiet check whenever the app comes to the foreground (Android usually keeps it
 * running in the background, so "on start" alone would rarely happen), then, when the user agrees, download the APK
 * and hand it to the system installer. Updates install only if signed with the same key as the installed app.
 */
class AppUpdater(private val context: Context, private val repo: String, private val scope: CoroutineScope) {
    sealed interface State {
        data object Idle : State
        data class Available(val release: GitHubReleases.Release) : State
        data class Downloading(val release: GitHubReleases.Release, val done: Long, val total: Long) : State
        /** Downloaded, but the user still has to allow this app to install apps. */
        data class NeedsPermission(val release: GitHubReleases.Release) : State
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
    private val apkFile get() = File(context.cacheDir, "updates/update.apk")

    /** Looks for a newer release, at most once per [CHECK_INTERVAL_MS]; failures (offline, rate limit) are ignored. */
    private fun checkIfDue() {
        if (repo.isEmpty() || _state.value != State.Idle || checkJob?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (lastCheck != 0L && now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now
        checkJob = scope.launch(Dispatchers.IO) {
            apkFile.delete() // left over from an earlier update
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
        _state.value = State.Idle
    }

    fun updateNow(release: GitHubReleases.Release) {
        job = scope.launch(Dispatchers.IO) {
            try {
                download(release)
                if (context.packageManager.canRequestPackageInstalls()) install()
                else _state.value = State.NeedsPermission(release)
            } catch (e: CancellationException) {
                apkFile.delete()
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** The system screen where the user allows this app to install updates. */
    fun permissionSettingsIntent() =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * The app came to the foreground: continue a pending update if the user just allowed installs, otherwise
     * check for a new release when one is due.
     */
    fun onResume() {
        if (_state.value !is State.NeedsPermission) return checkIfDue()
        if (context.packageManager.canRequestPackageInstalls()) {
            scope.launch(Dispatchers.IO) {
                try {
                    install()
                } catch (e: Exception) {
                    _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    private suspend fun download(release: GitHubReleases.Release) {
        _state.value = State.Downloading(release, 0, release.apkSize)
        apkFile.parentFile!!.mkdirs()
        // GitHub redirects asset downloads to its storage host; HttpURLConnection follows https → https.
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode} downloading the update")
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
        var done = 0L
        connection.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    _state.value = State.Downloading(release, done, total)
                    currentCoroutineContext().ensureActive()
                }
            }
        }
        if (total > 0 && done != total) throw IOException("Download incomplete ($done of $total bytes)")
    }

    private fun install() {
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
                apkFile.delete()
                _state.value = State.Idle
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = State.Idle // cancelled in the system dialog
            else -> _state.value = State.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed",
            )
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
        const val SNOOZE_MS = 24 * 60 * 60 * 1000L
        const val KEY_SNOOZED_VERSION = "snoozed_version"
        const val KEY_SNOOZED_UNTIL = "snoozed_until"
    }
}

/** Receives install session results (declared in the manifest, not exported). */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as FoodApp).updater.onInstallStatus(context, intent)
    }
}
