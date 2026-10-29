@file:OptIn(ExperimentalMaterial3Api::class)

package de.notifyprio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import de.notifyprio.data.RuleAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun RuleAction.label(inherit: String = "Normal"): String = when (this) {
    RuleAction.DEFAULT -> inherit
    RuleAction.PRIORITY -> "Wichtig"
    RuleAction.BUNDLE -> "Bündeln"
    RuleAction.SUPPRESS -> "Blocken"
}

fun RuleAction.description(): String = when (this) {
    RuleAction.DEFAULT -> "Keine eigene Regel"
    RuleAction.PRIORITY -> "Kommt immer durch"
    RuleAction.BUNDLE -> "Wird gebündelt"
    RuleAction.SUPPRESS -> "Wird entfernt (im Protokoll sichtbar)"
}

/** Vier-Wege-Auswahl: Normal/Wie App · Wichtig · Bündeln · Blocken */
@Composable
fun ActionSelector(
    selected: RuleAction,
    onSelect: (RuleAction) -> Unit,
    inheritLabel: String = "Normal",
) {
    val options = RuleAction.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, action ->
            SegmentedButton(
                selected = action == selected,
                onClick = { onSelect(action) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = {
                    Text(
                        text = action.label(inheritLabel),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = pkg) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    val img = bitmap
    if (img != null) {
        Image(bitmap = img, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}
