package com.meetmate.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.meetmate.app.notification.NotificationHelper

/** Things that stop reminders from working, each with a one-tap fix. */
enum class ReminderIssue(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val action: String
) {
    NOTIFICATIONS(
        Icons.Rounded.NotificationsOff,
        "Notifications are off",
        "MeetMate can't remind you about meetings until notifications are allowed.",
        "Allow"
    ),
    EXACT_ALARMS(
        Icons.Rounded.AlarmOff,
        "Reminders may be late",
        "Allow “Alarms & reminders” so reminders ring at the exact minute.",
        "Allow"
    ),
    FULL_SCREEN(
        Icons.Rounded.Smartphone,
        "Alarm screen is blocked",
        "Allow full-screen alerts so reminders can wake the screen when your phone is locked.",
        "Allow"
    );

    companion object {
        fun current(context: Context): List<ReminderIssue> = buildList {
            if (!NotificationHelper.canPostNotifications(context)) add(NOTIFICATIONS)
            if (!NotificationHelper.canScheduleAlarms(context)) add(EXACT_ALARMS)
            if (!NotificationHelper.canUseFullScreenIntent(context)) add(FULL_SCREEN)
        }
    }
}

@Composable
fun ReminderIssueCard(issue: ReminderIssue, onFix: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                issue.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    issue.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    issue.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
            TextButton(onClick = onFix) {
                Text(issue.action, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}
