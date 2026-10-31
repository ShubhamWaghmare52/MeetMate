package com.meetmate.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** "New meeting" FAB that shrinks to just the icon while the list is scrolled. */
@Composable
fun AnimatedFab(
    onClick: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        text = { Text("New meeting", style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 10.dp
        )
    )
}
