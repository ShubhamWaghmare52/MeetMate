package com.meetmate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meetmate.app.data.Meeting
import com.meetmate.app.ui.theme.Coral
import com.meetmate.app.ui.theme.HeroGradientEnd
import com.meetmate.app.ui.theme.HeroGradientMid
import com.meetmate.app.ui.theme.HeroGradientStart
import com.meetmate.app.util.TimeLabels
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Gradient hero card for the very next meeting, with a live countdown. */
@Composable
fun NextUpCard(
    meeting: Meeting,
    occurrenceMillis: Long,
    nowMillis: Long,
    onClick: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(occurrenceMillis),
        ZoneId.systemDefault()
    )
    val dayLabel = TimeLabels.dayLabel(dateTime.toLocalDate(), LocalDate.now())
    val shape = MaterialTheme.shapes.large

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(HeroGradientStart, HeroGradientMid, HeroGradientEnd))
            )
            .clickable(onClick = onClick)
    ) {
        // Decorative rings in the corner, echoing the clock in the logo.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 48.dp, y = (-48).dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 24.dp, y = (-24).dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Coral)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NEXT UP",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = TimeLabels.countdown(occurrenceMillis, nowMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = meeting.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$dayLabel · ${dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                if (meeting.personName.isNotBlank()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Avatar(name = meeting.personName, size = 24.dp, onDark = true)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = meeting.personName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (meeting.meetingLink.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HeroGradientEnd)
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join meeting", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
