package app.gridlauncher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.data.currentWifiName
import app.gridlauncher.data.PageOps
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.Screen
import app.gridlauncher.ui.home.HomeScreen
import app.gridlauncher.ui.settings.AppPickerScreen
import app.gridlauncher.ui.settings.ArrangeAppsScreen
import app.gridlauncher.ui.settings.FontPickerScreen
import app.gridlauncher.ui.settings.HiddenAppsScreen
import app.gridlauncher.ui.settings.SettingsScreen
import app.gridlauncher.ui.theme.FontCatalog
import app.gridlauncher.ui.theme.LauncherTheme
import app.gridlauncher.ui.theme.Palette
import app.gridlauncher.widgets.WidgetController
import app.gridlauncher.widgets.WidgetRequests
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity(), WidgetRequests {

    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgets: WidgetController
    private val host get() = launcherApp.widgetHost

    private val pendingPrefs by lazy { getSharedPreferences("pending_widget", MODE_PRIVATE) }

    /**
     * A widget id waiting for the bind permission dialog or its configuration screen. Kept on disk,
     * not in the saved-state bundle, so it survives the process being killed while that screen is
     * open, and it is cleared only after the widget is saved, so orphan cleanup never removes it.
     */
    private var pendingWidgetId: Int
        get() = pendingPrefs.getInt(KEY_PENDING, NO_ID)
        set(value) {
            pendingPrefs.edit(commit = true) { putInt(KEY_PENDING, value) }
        }

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId
        if (id == NO_ID) return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            configureOrPlace(id)
        } else {
            pendingWidgetId = NO_ID
            widgets.delete(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        widgets = WidgetController(this, host, this)

        // Free widget ids left over from a widget that never finished being added.
        lifecycleScope.launch {
            val settings = vm.settings.filterNotNull().first()
            // Read the pending id after the settings: an id is cleared only once its widget is saved.
            val pending = pendingWidgetId
            val placed = PageOps.appWidgetIds(settings.pages)
            // Keep ids a pending Undo may still bring back, too.
            // And ids saved setups hold, so applying one brings its widgets back.
            val saved = app.gridlauncher.data.SetupStore.widgetIdsInUse(this@MainActivity)
            val keep = placed + vm.heldWidgetIds + saved + (if (pending == NO_ID) emptySet() else setOf(pending))
            widgets.deleteOrphans(keep)
        }

        setContent { LauncherRoot(vm, widgets) }

        // Locked apps open only after the fingerprint (or the phone's screen lock).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.unlockRequests.collect(::unlock)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { host.startListening() }.onFailure { Log.w(TAG, "Widget host failed to start", it) }
        // Screens with a time or Wi-Fi rule are hidden outside it: check them on every return.
        vm.refreshRuleContext(currentWifiName(this))
    }

    override fun onStop() {
        super.onStop()
        // Another app is now in front, so the next Home press is a return, not a second press.
        wasFocusedBeforeIntent = false
        runCatching { host.stopListening() }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onRestart() {
        super.onRestart()
        // Back on home from another app or from a locked screen: let screen rules pick the screen.
        vm.applyScreenRules(currentWifiName(this))
    }


    /** Whether the launcher window had focus, i.e. was in front, just before the last pause. */
    private var wasFocusedBeforeIntent = false

    override fun onPause() {
        wasFocusedBeforeIntent = hasWindowFocus()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.onHomePressed(alreadyOnTop = wasFocusedBeforeIntent)
        }
    }

    private fun unlock(request: LauncherViewModel.UnlockRequest) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            vm.toast("Set up a screen lock or fingerprint to open locked apps")
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = request.onUnlocked()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Open ${request.label}")
                .setSubtitle("This app is locked in Grid Launcher")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    /** Blurs the wallpaper behind the window, on phones that support cross-window blur. */
    fun applyWallpaperBlur(on: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val supported = getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        val radius = if (on && supported) (48 * resources.displayMetrics.density).toInt() else 0
        if (radius > 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        window.attributes = window.attributes.apply { blurBehindRadius = radius }
    }

    // ---- Adding app widgets ------------------------------------------------------------------

    override fun requestAdd(info: AppWidgetProviderInfo) {
        val id = runCatching { host.allocateAppWidgetId() }.getOrElse {
            vm.toast("Couldn't add this widget")
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }
            .getOrDefault(false)
        pendingWidgetId = id
        pendingPrefs.edit(commit = true) { putString(KEY_PENDING_PAGE, vm.currentPage.value) }
        if (bound) {
            configureOrPlace(id)
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        try {
            bindWidget.launch(intent)
        } catch (e: ActivityNotFoundException) {
            pendingWidgetId = NO_ID
            widgets.delete(id)
            vm.toast("Couldn't add this widget")
        }
    }

    private fun configureOrPlace(id: Int) {
        val info = widgets.providerInfo(id)
        if (info == null) {
            pendingWidgetId = NO_ID
            widgets.delete(id)
            vm.toast("Couldn't add this widget")
            return
        }
        if (needsConfiguration(info)) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE, null)
            } catch (e: Exception) {
                // ActivityNotFoundException or SecurityException from the provider's activity.
                Log.w(TAG, "Widget configuration failed to start", e)
                pendingWidgetId = NO_ID
                widgets.delete(id)
                vm.toast("Couldn't set up this widget")
            }
        } else {
            place(id, info)
        }
    }

    private fun needsConfiguration(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
            if (optional) return false
        }
        return true
    }

    private fun place(id: Int, info: AppWidgetProviderInfo) {
        val (w, h) = widgets.defaultSizeDp(info)
        val saved = vm.addAppWidget(id, w, h, pendingPrefs.getString(KEY_PENDING_PAGE, null))
        lifecycleScope.launch {
            saved.join()
            if (pendingWidgetId == id) pendingWidgetId = NO_ID
        }
    }

    @Deprecated("Widget configuration activities can only be started through AppWidgetHost, which reports here.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE) return
        val id = pendingWidgetId.takeIf { it != NO_ID }
            ?: data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, NO_ID)
            ?: NO_ID
        if (id == NO_ID) return
        val info = widgets.providerInfo(id)
        if (resultCode == RESULT_OK && info != null) {
            place(id, info)
        } else {
            pendingWidgetId = NO_ID
            widgets.delete(id)
        }
    }

    companion object {
        private const val TAG = "GridLauncher"
        private const val NO_ID = -1
        private const val KEY_PENDING = "pending_widget_id"
        private const val KEY_PENDING_PAGE = "pending_widget_page"
        private const val REQUEST_CONFIGURE = 0x5743
    }
}

@Composable
private fun LauncherRoot(vm: LauncherViewModel, widgets: WidgetController) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val loaded = settings

    val activity = LocalActivity.current as? MainActivity
    val blur = screen == Screen.HOME && loaded?.showWallpaper == true && loaded.wallpaperBlur
    LaunchedEffect(blur) { activity?.applyWallpaperBlur(blur) }

    LauncherTheme(
        fontName = loaded?.fontFamily ?: FontCatalog.DEFAULT,
        accent = loaded?.accentColor ?: LauncherSettings.DEFAULT_ACCENT,
        widgetCorner = loaded?.widgetCorner ?: 22f,
    ) {
        // A home screen never closes on Back: Back leaves sub-screens and edit mode only.
        BackHandler(enabled = true) {
            when {
                screen != Screen.HOME -> vm.back()
                editing -> vm.exitEdit()
                // On home, Back returns to the first screen and its top, like Home does.
                else -> vm.goToFirstScreen()
            }
        }
        val background = if (screen == Screen.HOME && loaded?.showWallpaper == true) {
            Palette.Ground.copy(alpha = loaded.wallpaperDim)
        } else {
            Palette.Ground
        }
        Box(Modifier.fillMaxSize().background(background)) {
            if (loaded == null) return@Box
            when (screen) {
                Screen.HOME -> HomeScreen(vm, loaded, widgets)
                Screen.SETTINGS -> SettingsScreen(vm, loaded)
                Screen.FONTS -> FontPickerScreen(vm, loaded)
                Screen.HIDDEN_APPS -> HiddenAppsScreen(vm, loaded)
                Screen.ARRANGE_APPS -> ArrangeAppsScreen(vm, loaded)
                Screen.PICK_APPS -> AppPickerScreen(vm, loaded)
                Screen.TUTORIALS -> app.gridlauncher.ui.settings.TutorialsScreen(vm)
                Screen.GROUPS -> app.gridlauncher.ui.settings.GroupScreensScreen(vm, loaded)
                Screen.SETUPS -> app.gridlauncher.ui.settings.SetupsScreen(vm)
            }
            // A share code as QR codes, over whichever screen asked for it.
            val sheet by vm.shareSheet.collectAsStateWithLifecycle()
            sheet?.let { app.gridlauncher.ui.share.ShareCodeSheet(it.code, it.title, it.what, vm::closeShareCode) }
        }
    }
}
