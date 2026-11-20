package app.gridlauncher.data

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Lists launchable apps for every profile on the device (personal and work), keeps the list
 * current through [LauncherApps.Callback], and reads and starts app shortcuts.
 */
class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val icons: IconLoader,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    /** Bumped whenever icons or shortcuts may have changed, so the UI reloads them. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val shortcutCache = ConcurrentHashMap<String, List<ShortcutInfo>>()
    private var refreshJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = packagesChanged(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = packagesChanged(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = packagesChanged(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packagesChanged(*packageNames)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packagesChanged(*packageNames)
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
            packagesChanged(*packageNames)
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
            packagesChanged(*packageNames)
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            invalidateShortcuts(packageName)
        }
    }

    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // A work profile was added, removed, paused, resumed or unlocked.
            invalidateAllShortcuts()
            refresh()
        }
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }
        ContextCompat.registerReceiver(context, profileReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            _apps.value = withContext(Dispatchers.IO) { loadApps() }
        }
    }

    private fun packagesChanged(vararg packageNames: String) {
        packageNames.forEach {
            icons.invalidate(it)
            invalidateShortcutCache(it)
            IconPacks.forget(it)
        }
        _version.update { it + 1 }
        refresh()
    }

    private fun loadApps(): List<AppEntry> {
        val me = Process.myUserHandle()
        val profiles = runCatching { launcherApps.profiles }.getOrNull().orEmpty().ifEmpty { listOf(me) }
        val result = ArrayList<AppEntry>()
        for (user in profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot list apps for $user", e)
                emptyList()
            }
            for (info in activities) {
                val component = info.componentName
                if (component.packageName == context.packageName) continue
                val primary = user == me
                val raw = info.label?.toString()?.trim().orEmpty().ifEmpty { component.packageName }
                val label = if (primary) raw else context.packageManager.getUserBadgedLabel(raw, user).toString()
                val flags = info.applicationInfo.flags
                val system = flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                    flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                result += AppEntry(
                    key = "${component.flattenToShortString()}#$serial",
                    label = label,
                    component = component,
                    user = user,
                    isPrimaryUser = primary,
                    isSystemApp = system,
                )
            }
        }
        return result.distinctBy { it.key }
    }

    // ---- Shortcuts -------------------------------------------------------------------------

    /** True only while this app is the default home app; Android limits shortcuts to it. */
    fun hasShortcutPermission(): Boolean =
        runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun cachedShortcuts(app: AppEntry): List<ShortcutInfo>? = shortcutCache[app.key]

    suspend fun shortcuts(app: AppEntry): List<ShortcutInfo> {
        shortcutCache[app.key]?.let { return it }
        return withContext(Dispatchers.IO) {
            if (!hasShortcutPermission()) return@withContext emptyList()
            // A locked or paused work profile has no shortcuts right now; ask again once it's back.
            if (!isProfileReady(app.user)) return@withContext emptyList()
            val list = queryShortcuts(app)
            shortcutCache[app.key] = list
            list
        }
    }

    private fun isProfileReady(user: UserHandle): Boolean =
        runCatching { userManager.isUserUnlocked(user) && !userManager.isQuietModeEnabled(user) }.getOrDefault(false)

    private fun queryShortcuts(app: AppEntry): List<ShortcutInfo> {
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val found = try {
            launcherApps.getShortcuts(query, app.user).orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: IllegalStateException) {
            emptyList()
        }
        return found
            .filter { it.isEnabled && !it.shortLabel.isNullOrBlank() }
            .sortedWith(compareBy<ShortcutInfo>({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
            .take(MAX_SHORTCUTS)
    }

    private fun invalidateShortcutCache(packageName: String) {
        shortcutCache.keys.removeAll { it.startsWith("$packageName/") }
    }

    fun invalidateShortcuts(packageName: String) {
        invalidateShortcutCache(packageName)
        _version.update { it + 1 }
    }

    /** Asks every app icon and shortcut on screen to reload, after an icon pack change. */
    fun reloadContent() {
        _version.update { it + 1 }
    }

    fun invalidateAllShortcuts() {
        shortcutCache.clear()
        _version.update { it + 1 }
    }

    // ---- Actions ---------------------------------------------------------------------------

    fun launchApp(app: AppEntry, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startMainActivity(app.component, app.user, sourceBounds, options)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun launchShortcut(shortcut: ShortcutInfo, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startShortcut(shortcut, sourceBounds, options)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalStateException) {
        false
    }

    fun openAppInfo(app: AppEntry, sourceBounds: Rect?): Boolean = try {
        launcherApps.startAppDetailsActivity(app.component, app.user, sourceBounds, null)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun uninstall(app: AppEntry): Boolean = try {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    companion object {
        private const val TAG = "AppRepository"
        const val MAX_SHORTCUTS = 5
    }
}
