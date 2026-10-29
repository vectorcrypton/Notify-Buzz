package de.notifyprio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.notifyprio.data.FocusMode
import de.notifyprio.data.RuleAction
import de.notifyprio.data.RulesState
import de.notifyprio.system.SetupHelper

private fun FocusMode.title() = when (this) {
    FocusMode.OFF -> "Aus"
    FocusMode.FOCUS -> "Fokus"
    FocusMode.QUIET -> "Ruhe"
}

private fun FocusMode.description() = when (this) {
    FocusMode.OFF -> "Nur deine App- und Kanal-Regeln gelten."
    FocusMode.FOCUS -> "Nur „Wichtig“ kommt durch. Alles andere wird still gebündelt (alle Apps in einer Gruppe), „Blocken“ wird entfernt."
    FocusMode.QUIET -> "Nur „Wichtig“ kommt durch. Alles andere wird entfernt – im Protokoll bleibt es abrufbar."
}

@Composable
fun FocusScreen(
    rules: RulesState,
    status: SetupStatus,
    onMode: (FocusMode) -> Unit,
    onMirrorDnd: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val priorityCount = rules.appActions.count { it.value == RuleAction.PRIORITY } +
        rules.channelActions.count { it.value == RuleAction.PRIORITY }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Fokus-Modus", style = MaterialTheme.typography.headlineSmall)

        FocusMode.entries.forEach { mode ->
            Card(Modifier.fillMaxWidth().clickable { onMode(mode) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = rules.focusMode == mode, onClick = { onMode(mode) })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(mode.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            mode.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            "$priorityCount App-/Kanal-Regeln stehen auf „Wichtig“ und kommen in jedem Modus durch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SwitchRow(
            title = "System-„Nicht stören“ mitschalten",
            subtitle = "Fokus → Nur Priorität, Ruhe → Nur Wecker. Verhindert den kurzen Ton vor dem Entfernen, " +
                "dämpft aber auch „Wichtig“-Töne.",
            checked = rules.mirrorSystemDnd,
            enabled = status.dndAccess,
            onChange = onMirrorDnd,
        )
        if (!status.dndAccess) {
            TextButton(onClick = { SetupHelper.openDndAccess(context) }) { Text("„Nicht stören“-Zugriff erteilen") }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}
