package com.meetmate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetmate.app.util.TimeLabels

private val AvatarPalette = listOf(
    Color(0xFF6366F1) to Color(0xFFE0E7FF),
    Color(0xFFE85D4A) to Color(0xFFFFE4DE),
    Color(0xFF0E9F6E) to Color(0xFFD1FAE5),
    Color(0xFFD97706) to Color(0xFFFEF3C7),
    Color(0xFFDB2777) to Color(0xFFFCE7F3),
    Color(0xFF0284C7) to Color(0xFFE0F2FE)
)

/** Initials in a tinted circle; the colour is stable per name. */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    onDark: Boolean = false
) {
    val (fg, bg) = AvatarPalette[(name.lowercase().hashCode() and Int.MAX_VALUE) % AvatarPalette.size]
    // Pastel chips glare on dark surfaces, so flip to a tinted chip with light text there.
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    onDark -> Color.White.copy(alpha = 0.18f)
                    isDark -> fg.copy(alpha = 0.3f)
                    else -> bg
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = TimeLabels.initials(name).ifEmpty { "?" },
            color = if (onDark || isDark) Color.White else fg,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
