package com.meetmate.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmate.app.notification.AlarmService
import com.meetmate.app.notification.NotificationHelper
import com.meetmate.app.notification.RingingAlarm
import com.meetmate.app.ui.components.Avatar
import com.meetmate.app.ui.theme.Coral
import com.meetmate.app.ui.theme.HeroGradientEnd
import com.meetmate.app.ui.theme.HeroGradientMid
import com.meetmate.app.ui.theme.HeroGradientStart
import com.meetmate.app.ui.theme.MeetMateTheme
import com.meetmate.app.util.Links
import com.meetmate.app.util.TimeLabels
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Full-screen alarm shown over the lock screen while a reminder rings. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleJoin(intent)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        enableEdgeToEdge()

        setContent {
            MeetMateTheme(darkTheme = true) {
                val alarm by AlarmService.ringing.collectAsStateWithLifecycle()
                // Closes itself once the alarm is dismissed, snoozed or times out anywhere.
                LaunchedEffect(alarm) { if (alarm == null) finish() }
                alarm?.let { ringing ->
                    AlarmScreen(
                        alarm = ringing,
                        onDismiss = { AlarmService.dismiss(this) },
                        onSnooze = {
                            AlarmService.snooze(this)
                            Toast.makeText(
                                this,
                                "Snoozed for ${NotificationHelper.SNOOZE_MINUTES} minutes",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onJoin = { joinAndClose(ringing.meetingLink) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleJoin(intent)
    }

    /** The notification's Join button lands here so it can stop the ringing before opening the link. */
    private fun handleJoin(intent: Intent?): Boolean {
        if (intent?.action != ACTION_JOIN) return false
        joinAndClose(intent.getStringExtra(EXTRA_LINK).orEmpty())
        return true
    }

    private fun joinAndClose(link: String) {
        AlarmService.dismiss(this)
        Links.open(this, link)
        finish()
    }

    companion object {
        const val ACTION_JOIN = "com.meetmate.app.action.JOIN"
        const val EXTRA_LINK = "link"
    }
}

@Composable
private fun AlarmScreen(
    alarm: RingingAlarm,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onJoin: () -> Unit
) {
    val time = Instant.ofEpochMilli(alarm.occurrenceMillis).atZone(ZoneId.systemDefault())
    val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ring_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HeroGradientStart, HeroGradientMid, HeroGradientEnd)))
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .scale(pulse)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Coral)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (alarm.snoozed) "SNOOZED REMINDER" else "MEETING REMINDER",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = time.format(DateTimeFormatter.ofPattern("h:mm")),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = MaterialTheme.typography.displaySmall.fontSize * 2
                ),
                color = Color.White
            )
            Text(
                text = TimeLabels.countdown(alarm.occurrenceMillis, System.currentTimeMillis())
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = alarm.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (alarm.personName.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name = alarm.personName, size = 28.dp, onDark = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(alarm.personName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (alarm.meetingLink.isNotBlank()) {
                Button(
                    onClick = onJoin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join meeting", style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Snooze, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${NotificationHelper.SNOOZE_MINUTES} min", style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HeroGradientEnd)
                ) {
                    Icon(Icons.Rounded.AlarmOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dismiss", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
