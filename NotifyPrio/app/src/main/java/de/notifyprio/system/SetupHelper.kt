package de.notifyprio.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Statusabfragen und Sprünge in System-/HyperOS-Einstellungen. */
object SetupHelper {

    fun postNotificationsGranted(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun secureSettingsGranted(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun labelOf(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(pkg)

    // ------------------------------------------------------------- Sprünge

    fun openListenerSettings(ctx: Context) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ListenerGuard.component(ctx).flattenToString())
        val general = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        ctx.tryStart(detail, general)
    }

    fun openDndAccess(ctx: Context) =
        ctx.tryStart(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    fun requestBatteryExemption(ctx: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        ctx.tryStart(direct, list, appDetails(ctx))
    }

    /** Xiaomi/HyperOS: Akku-Sparmodus pro App ("Keine Einschränkungen"). */
    fun openHyperOsBattery(ctx: Context) {
        val powerKeeper = Intent().setComponent(
            ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
        ).putExtra("package_name", ctx.packageName)
            .putExtra("package_label", labelOf(ctx, ctx.packageName))
        ctx.tryStart(powerKeeper, appDetails(ctx))
    }

    /** Xiaomi/HyperOS: Autostart-Verwaltung. */
    fun openHyperOsAutostart(ctx: Context) {
        val autostart = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        ctx.tryStart(autostart, appDetails(ctx))
    }

    /** Systemseite eines einzelnen Benachrichtigungskanals einer Fremd-App. */
    fun openChannelSettings(ctx: Context, pkg: String, channelId: String) {
        val channel = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        ctx.tryStart(channel, app)
    }

    private fun appDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    private fun Context.tryStart(vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // nächste Variante probieren (HyperOS ändert interne Activities gern)
            }
        }
        return false
    }
}
