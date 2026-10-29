package de.notifyprio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.notifyprio.system.ListenerGuard
import de.notifyprio.system.SetupHelper

@Composable
fun SetupScreen(
    status: SetupStatus,
    onRequestPostNotifications: () -> Unit,
    onToggleService: () -> Unit,
) {
    val context = LocalContext.current
    val pkg = context.packageName
    val component = ListenerGuard.component(context).flattenToString()

    val adb = """
        adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS
        adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS
        adb shell cmd notification allow_listener $component
        adb shell cmd notification allow_dnd $pkg
        adb shell dumpsys deviceidle whitelist +$pkg
        adb shell cmd appops set $pkg RUN_IN_BACKGROUND allow
        adb shell cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow
    """.trimIndent()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Einrichtung", style = MaterialTheme.typography.headlineSmall)

        SetupCard(
            title = "Benachrichtigungszugriff",
            ok = status.listenerEnabled,
            text = if (status.listenerEnabled)
                "Aktiv · Dienst verbunden: " + (if (status.listenerConnected) "ja" else "nein (wird automatisch neu angebunden)")
            else "Pflicht. Ohne diesen Zugriff sieht die App keine Benachrichtigungen. " +
                "Ist der Schalter ausgegraut: App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“.",
            buttonLabel = "Zugriff öffnen",
            onClick = { SetupHelper.openListenerSettings(context) },
        )
        SetupCard(
            title = "Eigene Benachrichtigungen (POST_NOTIFICATIONS)",
            ok = status.postNotifications,
            text = "Nötig, damit das Bündel in der Leiste erscheinen kann.",
            buttonLabel = if (status.postNotifications) null else "Erlauben",
            onClick = onRequestPostNotifications,
        )
        SetupCard(
            title = "Dauerbetrieb (Foreground Service)",
            ok = status.serviceEnabled,
            text = "Typ „specialUse“. Hält die App unter HyperOS am Leben und prüft minütlich die Listener-Verbindung.",
            buttonLabel = if (status.serviceEnabled) "Ausschalten" else "Einschalten",
            onClick = onToggleService,
        )
        SetupCard(
            title = "Akku-Optimierung (Android)",
            ok = status.batteryUnrestricted,
            text = "Die App sollte von der Akku-Optimierung ausgenommen sein.",
            buttonLabel = if (status.batteryUnrestricted) null else "Ausnahme anfordern",
            onClick = { SetupHelper.requestBatteryExemption(context) },
        )
        SetupCard(
            title = "HyperOS: Akku sparen",
            ok = null,
            text = "Stelle für diese App „Keine Einschränkungen“ ein (lässt sich nicht automatisch prüfen).",
            buttonLabel = "HyperOS-Akkueinstellung",
            onClick = { SetupHelper.openHyperOsBattery(context) },
        )
        SetupCard(
            title = "HyperOS: Autostart",
            ok = null,
            text = "Autostart für diese App aktivieren, damit sie nach dem Neustart wieder startet.",
            buttonLabel = "Autostart öffnen",
            onClick = { SetupHelper.openHyperOsAutostart(context) },
        )
        SetupCard(
            title = "„Nicht stören“-Zugriff (optional)",
            ok = status.dndAccess,
            text = "Nur nötig für „System-Nicht-stören mitschalten“.",
            buttonLabel = if (status.dndAccess) null else "Zugriff erteilen",
            onClick = { SetupHelper.openDndAccess(context) },
        )
        SetupCard(
            title = "WRITE_SECURE_SETTINGS (optional, nur per ADB)",
            ok = status.secureSettings,
            text = "Erlaubt der App, ihren Benachrichtigungszugriff nach einem Systemkill selbst wieder zu aktivieren.",
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("ADB-Befehle (PC/Mac)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Text lang drücken zum Kopieren. Auf Xiaomi/HyperOS muss in den Entwickleroptionen auch " +
                        "„USB-Debugging (Sicherheitseinstellungen)“ aktiv sein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(adb, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    title: String,
    ok: Boolean?,
    text: String,
    buttonLabel: String? = null,
    onClick: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            val (icon, tint) = when (ok) {
                true -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                false -> Icons.Default.Warning to MaterialTheme.colorScheme.error
                null -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (buttonLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onClick) { Text(buttonLabel) }
                }
            }
        }
    }
}
