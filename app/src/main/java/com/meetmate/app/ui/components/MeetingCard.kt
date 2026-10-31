package com.meetmate.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetmate.app.data.Meeting
import com.meetmate.app.ui.theme.MeetMateTheme
import com.meetmate.app.util.TimeLabels
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hourFormatter = DateTimeFormatter.ofPattern("h:mm")
private val amPmFormatter = DateTimeFormatter.ofPattern("a")

/** A meeting row: time on the left, an accent rail, then title, person and reminder. */
@Composable
fun MeetingCard(
    meeting: Meeting,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(meeting.dateTimeMillis),
        ZoneId.systemDefault()
    )
    val isPast = meeting.isCompleted || meeting.dateTimeMillis < nowMillis
    val startsSoon = !isPast && meeting.dateTimeMillis - nowMillis < 60 * 60_000L
    val extra = MeetMateTheme.extraColors

    val railColor = when {
        meeting.isCompleted -> extra.success
        isPast -> MaterialTheme.colorScheme.outline
        startsSoon -> extra.accent
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .alpha(if (isPast) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(56.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = dateTime.format(hourFormatter),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateTime.format(amPmFormatter).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(railColor)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (meeting.isCompleted) TextDecoration.LineThrough else null
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (meeting.personName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name = meeting.personName, size = 22.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = meeting.personName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    when {
                        meeting.isCompleted -> StatusPill(
                            text = "Done",
                            color = extra.success,
                            icon = { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(12.dp), tint = extra.success) }
                        )
                        startsSoon -> StatusPill(
                            text = TimeLabels.countdown(meeting.dateTimeMillis, nowMillis),
                            color = extra.accent
                        )
                        !isPast -> StatusPill(
                            text = TimeLabels.reminderShort(meeting.reminderMinutesBefore),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = {
                                Icon(
                                    Icons.Rounded.NotificationsActive,
                                    contentDescription = "Reminder",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
