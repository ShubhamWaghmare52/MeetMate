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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetmate.app.data.Meeting
import com.meetmate.app.data.Repeat
import com.meetmate.app.ui.theme.MeetMateTheme
import com.meetmate.app.util.TimeLabels
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hourFormatter = DateTimeFormatter.ofPattern("h:mm")
private val amPmFormatter = DateTimeFormatter.ofPattern("a")
private val spokenFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM, h:mm a")

/** A meeting row: time on the left, an accent rail, then title, person and status. */
@Composable
fun MeetingCard(
    meeting: Meeting,
    occurrenceMillis: Long,
    nowMillis: Long,
    onClick: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(occurrenceMillis), ZoneId.systemDefault())
    val isPast = meeting.isCompleted || occurrenceMillis < nowMillis
    val startsSoon = !isPast && occurrenceMillis - nowMillis < 60 * 60_000L
    val hasLink = meeting.meetingLink.isNotBlank()
    val extra = MeetMateTheme.extraColors

    val railColor = when {
        meeting.isCompleted -> extra.success
        isPast -> MaterialTheme.colorScheme.outline
        startsSoon -> extra.accent
        else -> MaterialTheme.colorScheme.primary
    }

    // One sentence for screen readers instead of a dozen separate fragments.
    val spoken = buildString {
        append(meeting.title)
        if (meeting.personName.isNotBlank()) append(", with ${meeting.personName}")
        append(", ${dateTime.format(spokenFormatter)}")
        if (meeting.repeat != Repeat.NONE) append(", repeats ${meeting.repeat.label.lowercase()}")
        when {
            meeting.isCompleted -> append(", done")
            startsSoon -> append(", starts ${TimeLabels.countdown(occurrenceMillis, nowMillis)}")
            !isPast -> append(", reminder ${TimeLabels.reminderLabel(meeting.reminderMinutesBefore)}")
        }
        if (meeting.category != "General") append(", ${meeting.category}")
        if (meeting.notes.isNotBlank()) append(", has notes")
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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Min)
                    .clearAndSetSemantics { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.widthIn(min = 56.dp)) {
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
                    if (meeting.personName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when {
                            meeting.isCompleted -> StatusPill("Done", extra.success, Icons.Rounded.CheckCircle)
                            startsSoon -> StatusPill(TimeLabels.countdown(occurrenceMillis, nowMillis), extra.accent)
                            !isPast -> StatusPill(
                                TimeLabels.reminderShort(meeting.reminderMinutesBefore),
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                Icons.Rounded.NotificationsActive
                            )
                        }
                        if (meeting.repeat != Repeat.NONE) {
                            StatusPill(meeting.repeat.label, MaterialTheme.colorScheme.primary, Icons.Rounded.Repeat)
                        }
                        if (meeting.category != "General") {
                            StatusPill(meeting.category, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (meeting.notes.isNotBlank()) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (hasLink && !isPast) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onJoin,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Join", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}
