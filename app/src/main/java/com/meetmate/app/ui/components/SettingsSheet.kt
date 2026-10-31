package com.meetmate.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meetmate.app.data.ThemeMode
import com.meetmate.app.notification.NotificationHelper
import com.meetmate.app.ui.theme.MeetMateTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    issues: List<ReminderIssue>,
    onFixIssue: (ReminderIssue) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            SheetLabel("Appearance")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == themeMode,
                        onClick = { onThemeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                    ) { Text(mode.label) }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SheetLabel("Reminders")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusRow("Notifications", ReminderIssue.NOTIFICATIONS !in issues) {
                    onFixIssue(ReminderIssue.NOTIFICATIONS)
                }
                StatusRow("Exact alarm timing", ReminderIssue.EXACT_ALARMS !in issues) {
                    onFixIssue(ReminderIssue.EXACT_ALARMS)
                }
                StatusRow("Alarm screen on lock screen", ReminderIssue.FULL_SCREEN !in issues) {
                    onFixIssue(ReminderIssue.FULL_SCREEN)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Reminders ring for up to a minute. Snooze delays them by ${NotificationHelper.SNOOZE_MINUTES} minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    val extra = MeetMateTheme.extraColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = if (ok) "On" else "Off",
            tint = if (ok) extra.success else extra.danger,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!ok) TextButton(onClick = onFix) { Text("Fix") }
    }
}
