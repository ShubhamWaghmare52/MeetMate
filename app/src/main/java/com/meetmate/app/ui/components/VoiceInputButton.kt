package com.meetmate.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.meetmate.app.ui.theme.MeetMateTheme

@Composable
fun VoiceInputButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MeetMateTheme.extraColors.accent
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse_scale"
    )
    val container by animateColorAsState(
        if (isListening) accent else MaterialTheme.colorScheme.primary,
        label = "mic_container"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(56.dp)) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.25f))
            )
        }
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = container,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = if (isListening) "Stop listening" else "Speak your meeting"
            )
        }
    }
}
