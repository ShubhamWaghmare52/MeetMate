package com.meetmate.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.meetmate.app.data.Meeting
import com.meetmate.app.ui.components.AnimatedFab
import com.meetmate.app.ui.components.EmptyKind
import com.meetmate.app.ui.components.EmptyState
import com.meetmate.app.ui.components.MeetingCard
import com.meetmate.app.ui.components.NextUpCard
import com.meetmate.app.ui.theme.MeetMateTheme
import com.meetmate.app.util.TimeLabels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class MeetingFilter(val label: String) { Upcoming("Upcoming"), Today("Today"), Past("Past") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    meetings: List<Meeting>,
    onAddClick: () -> Unit,
    onEditMeeting: (Meeting) -> Unit,
    onDeleteMeeting: (Meeting) -> Unit,
    onCompleteMeeting: (Meeting) -> Unit,
    onRestoreMeeting: (Meeting) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by rememberSaveable { mutableStateOf(MeetingFilter.Upcoming) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    // Re-evaluate "now" every 30s so countdowns and the Upcoming/Past split stay fresh.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    fun Meeting.date(): LocalDate = Instant.ofEpochMilli(dateTimeMillis).atZone(zone).toLocalDate()

    val upcoming = meetings.filter { !it.isCompleted && it.dateTimeMillis >= now }
    val todays = meetings.filter { it.date() == today }
    val past = meetings.filter { it.isCompleted || it.dateTimeMillis < now }.reversed()

    val searching = searchOpen && query.isNotBlank()
    val visible = when {
        searching -> meetings.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.personName.contains(query, ignoreCase = true)
        }
        else -> when (selectedFilter) {
            MeetingFilter.Upcoming -> upcoming
            MeetingFilter.Today -> todays
            MeetingFilter.Past -> past
        }
    }
    val hero = if (!searching && selectedFilter == MeetingFilter.Upcoming) upcoming.firstOrNull() else null
    val listItems = if (hero != null) visible.drop(1) else visible
    val sections = listItems.groupBy { it.date() }

    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun withUndo(message: String, original: Meeting) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onRestoreMeeting(original)
        }
    }

    Scaffold(
        floatingActionButton = { AnimatedFab(onClick = onAddClick, expanded = fabExpanded) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                Header(
                    searchOpen = searchOpen,
                    query = query,
                    onQueryChange = { query = it },
                    onToggleSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    },
                    upcomingCount = upcoming.size,
                    todayCount = todays.count { !it.isCompleted && it.dateTimeMillis >= now }
                )
            }

            if (!searching) {
                item(key = "tabs") {
                    FilterTabs(
                        selected = selectedFilter,
                        counts = mapOf(
                            MeetingFilter.Upcoming to upcoming.size,
                            MeetingFilter.Today to todays.size,
                            MeetingFilter.Past to past.size
                        ),
                        onSelect = { selectedFilter = it }
                    )
                }
            }

            if (hero != null) {
                item(key = "hero-${hero.id}") {
                    NextUpCard(
                        meeting = hero,
                        nowMillis = now,
                        onClick = { onEditMeeting(hero) },
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .animateItemPlacement()
                    )
                }
            }

            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        kind = when {
                            searching -> EmptyKind.Search
                            selectedFilter == MeetingFilter.Today -> EmptyKind.Today
                            selectedFilter == MeetingFilter.Past -> EmptyKind.Past
                            else -> EmptyKind.Upcoming
                        }
                    )
                }
            }

            sections.forEach { (date, dayMeetings) ->
                item(key = "section-$date") {
                    Text(
                        text = TimeLabels.dayLabel(date, today).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 14.dp, start = 4.dp)
                            .animateItemPlacement()
                    )
                }
                items(dayMeetings, key = { it.id }) { meeting ->
                    SwipeableMeetingRow(
                        meeting = meeting,
                        nowMillis = now,
                        onClick = { onEditMeeting(meeting) },
                        onComplete = {
                            onCompleteMeeting(meeting)
                            withUndo("Marked as done", meeting)
                        },
                        onDelete = {
                            onDeleteMeeting(meeting)
                            withUndo("Meeting deleted", meeting)
                        },
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    upcomingCount: Int,
    todayCount: Int
) {
    val focusRequester = remember { FocusRequester() }

    Column(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = TimeLabels.headerDate(LocalDate.now()).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = TimeLabels.greeting(LocalTime.now()),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            FilledTonalIconButton(
                onClick = onToggleSearch,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (searchOpen) "Close search" else "Search meetings"
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        AnimatedContent(
            targetState = searchOpen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "header_sub"
        ) { open ->
            if (open) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search by title or person") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester)
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                Text(
                    text = when {
                        upcomingCount == 0 -> "No upcoming meetings."
                        todayCount > 0 -> "$todayCount more today · $upcomingCount upcoming"
                        else -> "$upcomingCount upcoming"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FilterTabs(
    selected: MeetingFilter,
    counts: Map<MeetingFilter, Int>,
    onSelect: (MeetingFilter) -> Unit
) {
    // The selected pill should read as raised: lighter than the track in both themes.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val track = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    val pill = if (isDark) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(track)
            .padding(4.dp)
    ) {
        MeetingFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            val container by animateColorAsState(
                if (isSelected) pill else Color.Transparent,
                label = "tab_bg"
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tab_fg"
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(container)
                    .clickable { onSelect(filter) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(filter.label, style = MaterialTheme.typography.labelLarge, color = content)
                val count = counts[filter] ?: 0
                if (count > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else content
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableMeetingRow(
    meeting: Meeting,
    nowMillis: Long,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onComplete()
                    false // snap back; the list update moves the row
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )
    val extra = MeetMateTheme.extraColors

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = !meeting.isCompleted,
        backgroundContent = {
            val direction = state.dismissDirection
            val (color, icon, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd ->
                    Triple(extra.success, Icons.Rounded.Done, Alignment.CenterStart)
                else ->
                    Triple(extra.danger, Icons.Rounded.DeleteOutline, Alignment.CenterEnd)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        icon,
                        contentDescription = if (direction == SwipeToDismissBoxValue.StartToEnd) "Mark done" else "Delete",
                        tint = color,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) {
        MeetingCard(meeting = meeting, nowMillis = nowMillis, onClick = onClick)
    }
}
