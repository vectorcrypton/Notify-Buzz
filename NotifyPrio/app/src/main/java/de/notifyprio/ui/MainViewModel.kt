package de.notifyprio.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.notifyprio.data.CapturedNotification
import de.notifyprio.data.FilterManager
import de.notifyprio.data.FocusMode
import de.notifyprio.data.NotificationStore
import de.notifyprio.data.Outcome
import de.notifyprio.data.RuleAction
import de.notifyprio.data.RulesState
import de.notifyprio.service.BundleNotifier
import de.notifyprio.service.KeepAliveService
import de.notifyprio.service.PriorityListenerService
import de.notifyprio.system.DndController
import de.notifyprio.system.ListenerGuard
import de.notifyprio.system.SetupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppEntry(
    val pkg: String,
    val label: String,
    val platformCategory: Int = -1,
)

data class SetupStatus(
    val listenerEnabled: Boolean,
    val listenerConnected: Boolean,
    val postNotifications: Boolean,
    val serviceEnabled: Boolean,
    val batteryUnrestricted: Boolean,
    val dndAccess: Boolean,
    val secureSettings: Boolean,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val filters = FilterManager.get(app)
    private val store = NotificationStore.get(app)

    val rules: StateFlow<RulesState> = filters.state
    val items: StateFlow<List<CapturedNotification>> = store.items

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<SetupStatus> = _status.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.Default) {
            val ctx = getApplication<Application>()
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            _apps.value = pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
                .map {
                    AppEntry(
                        pkg = it.activityInfo.packageName,
                        label = it.loadLabel(pm).toString(),
                        platformCategory = it.activityInfo.applicationInfo.category,
                    )
                }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
        }
    }

    fun refreshStatus() {
        _status.value = readStatus()
    }

    private fun readStatus(): SetupStatus {
        val ctx = getApplication<Application>()
        return SetupStatus(
            listenerEnabled = ListenerGuard.isEnabledInSettings(ctx),
            listenerConnected = PriorityListenerService.connected,
            postNotifications = SetupHelper.postNotificationsGranted(ctx),
            serviceEnabled = KeepAliveService.isEnabled(ctx),
            batteryUnrestricted = SetupHelper.batteryUnrestricted(ctx),
            dndAccess = DndController.hasPolicyAccess(ctx),
            secureSettings = SetupHelper.secureSettingsGranted(ctx),
        )
    }

    // ---- Regeln
    fun setApp(pkg: String, action: RuleAction) = filters.setAppAction(pkg, action)
    fun setChannel(pkg: String, channelId: String, action: RuleAction) =
        filters.setChannelAction(pkg, channelId, action)

    fun setFocus(mode: FocusMode) = filters.setFocusMode(mode)
    fun setMirrorDnd(enabled: Boolean) = filters.setMirrorSystemDnd(enabled)

    // ---- Bündel / Protokoll
    fun openItem(item: CapturedNotification) {
        val ctx = getApplication<Application>()
        store.open(ctx, item)
        removeFromBundle(item)
    }

    fun dismissItem(item: CapturedNotification) = removeFromBundle(item)

    private fun removeFromBundle(item: CapturedNotification) {
        val ctx = getApplication<Application>()
        store.remove(item.key)
        BundleNotifier.cancelChild(ctx, item.key)
        BundleNotifier.updateSummary(ctx)
    }

    fun clear(outcome: Outcome) {
        store.clear(outcome)
        if (outcome == Outcome.BUNDLED) BundleNotifier.cancelAll(getApplication())
    }

    // ---- Setup
    fun toggleService() {
        val ctx = getApplication<Application>()
        KeepAliveService.setEnabled(ctx, !KeepAliveService.isEnabled(ctx))
        refreshStatus()
    }
}
