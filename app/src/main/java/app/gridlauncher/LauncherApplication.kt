package app.gridlauncher

import android.app.Application
import android.content.Context
import app.gridlauncher.data.AppRepository
import app.gridlauncher.data.IconLoader
import app.gridlauncher.data.SettingsRepository
import app.gridlauncher.widgets.LauncherWidgetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LauncherApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settings: SettingsRepository
        private set
    lateinit var icons: IconLoader
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var widgetHost: LauncherWidgetHost
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        icons = IconLoader(this)
        apps = AppRepository(this, appScope, icons)
        widgetHost = LauncherWidgetHost(this, WIDGET_HOST_ID)
        apps.start()
    }

    companion object {
        const val WIDGET_HOST_ID = 0x4C41
    }
}

val Context.launcherApp: LauncherApplication
    get() = applicationContext as LauncherApplication
