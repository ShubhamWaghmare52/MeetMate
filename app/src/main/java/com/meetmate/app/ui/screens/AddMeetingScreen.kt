package com.meetmate.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.meetmate.app.data.Meeting
import com.meetmate.app.data.MeetingCategories
import com.meetmate.app.data.MeetingDraft
import com.meetmate.app.data.Repeat
import android.provider.CalendarContract
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Link
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.meetmate.app.parser.NaturalLanguageParser
import com.meetmate.app.ui.components.VoiceInputButton
import com.meetmate.app.util.TimeLabels
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ExamplePhrases = listOf(
    "Lunch with Priya tomorrow at 1pm",
    "Standup every weekday at 10am",
    "Call with Alex at noon",
    "Review with Sam on 5 Nov at 11am",
    "Gym in 2 hours"
)

private val ReminderOptions = listOf(5, 15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeetingScreen(
    editMeeting: Meeting? = null,
    startWithVoice: Boolean = false,
    onSave: (MeetingDraft) -> Unit,
    onDelete: ((Meeting) -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isEditing = editMeeting != null
    val existingDateTime = editMeeting?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.dateTimeMillis), ZoneId.systemDefault())
    }

    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var lastParsed by remember { mutableStateOf<String?>(null) }

    var selectedDate by remember { mutableStateOf(existingDateTime?.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(existingDateTime?.toLocalTime()) }
    var personName by remember { mutableStateOf(editMeeting?.personName ?: "") }
    var meetingTitle by remember { mutableStateOf(editMeeting?.title ?: "") }
    var reminderMinutes by remember { mutableIntStateOf(editMeeting?.reminderMinutesBefore ?: 30) }
    var notes by remember { mutableStateOf(editMeeting?.notes ?: "") }
    var category by remember { mutableStateOf(editMeeting?.category ?: "General") }
    var repeat by remember { mutableStateOf(editMeeting?.repeat ?: Repeat.NONE) }
    var meetingLink by remember { mutableStateOf(editMeeting?.meetingLink ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var triedToSave by remember { mutableStateOf(false) }

    fun parseAndApply(text: String = inputText) {
        if (text.trim().length < 3) return
        val parsed = NaturalLanguageParser.parse(text)
        parsed.personName?.let { personName = it }
        parsed.date?.let { selectedDate = it }
        parsed.time?.let { selectedTime = it }
        parsed.title?.let { meetingTitle = it }
        parsed.repeat?.let { repeat = it }
        parsed.meetingLink?.let { meetingLink = it }
        lastParsed = text
        focusManager.clearFocus()
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { text ->
                    inputText = text
                    parseAndApply(text)
                }
        }
    }

    fun handleSave() {
        // Typed a sentence but never parsed it: fill the fields first so the user can review.
        if (inputText.isNotBlank() && lastParsed != inputText && meetingTitle.isBlank()) {
            parseAndApply()
            return
        }

        triedToSave = true
        if (meetingTitle.isBlank()) return
        if (selectedDate == null) {
            showDatePicker = true
            return
        }
        if (selectedTime == null) {
            showTimePicker = true
            return
        }

        val millis = NaturalLanguageParser.toEpochMillis(selectedDate!!, selectedTime!!)
        onSave(
            MeetingDraft(
                title = meetingTitle.trim(),
                personName = personName.trim(),
                dateTimeMillis = millis,
                reminderMinutesBefore = reminderMinutes,
                notes = notes.trim(),
                category = category,
                repeat = repeat,
                meetingLink = meetingLink.trim()
            )
        )
    }

    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your meeting…")
        }
        try {
            isListening = true
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            isListening = false
            Toast.makeText(context, "Voice input isn't available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // Opened from the "New meeting by voice" shortcut, tile or widget.
    LaunchedEffect(Unit) { if (startWithVoice && !isEditing) launchVoice() }

    fun addToCalendar() {
        val date = selectedDate ?: return
        val time = selectedTime ?: return
        val start = NaturalLanguageParser.toEpochMillis(date, time)
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, meetingTitle)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60_000L)
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                listOf(
                    personName.takeIf { it.isNotBlank() }?.let { "With $it" },
                    meetingLink.takeIf { it.isNotBlank() },
                    notes.takeIf { it.isNotBlank() }
                ).filterNotNull().joinToString("\n")
            )
            repeatRule(repeat)?.let { putExtra(CalendarContract.Events.RRULE, it) }
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) "Edit meeting" else "New meeting",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedDate != null && selectedTime != null && meetingTitle.isNotBlank()) {
                        IconButton(onClick = { addToCalendar() }) {
                            Icon(Icons.Rounded.EditCalendar, contentDescription = "Add to calendar")
                        }
                    }
                    if (isEditing && onDelete != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete meeting",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { handleSave() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEditing) "Save changes" else "Save meeting",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            if (!isEditing) {
                Composer(
                    text = inputText,
                    onTextChange = { inputText = it },
                    isListening = isListening,
                    parsed = lastParsed != null && lastParsed == inputText,
                    onParse = { parseAndApply() },
                    onMic = { launchVoice() },
                    onExample = { phrase ->
                        inputText = phrase
                        parseAndApply(phrase)
                    }
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            SectionLabel("Details")
            OutlinedTextField(
                value = meetingTitle,
                onValueChange = { meetingTitle = it },
                label = { Text("Title") },
                leadingIcon = { Icon(Icons.Rounded.Title, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                colors = fieldColors(),
                isError = triedToSave && meetingTitle.isBlank(),
                supportingText = if (triedToSave && meetingTitle.isBlank()) {
                    { Text("Give your meeting a title") }
                } else null
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = personName,
                onValueChange = { personName = it },
                label = { Text("With (optional)") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                colors = fieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = meetingLink,
                onValueChange = { meetingLink = it },
                label = { Text("Meeting link (optional)") },
                placeholder = { Text("meet.google.com/abc-defg-hij") },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = fieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                minLines = 2,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MeetingCategories.forEach { c ->
                    QuickChip(label = c, selected = category == c) { category = c }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SectionLabel("When")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                QuickChip("Today", selectedDate == today) { selectedDate = today }
                QuickChip("Tomorrow", selectedDate == today.plusDays(1)) { selectedDate = today.plusDays(1) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PickerTile(
                    icon = Icons.Rounded.CalendarMonth,
                    label = "Date",
                    value = selectedDate?.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                    placeholder = "Pick date",
                    isError = triedToSave && selectedDate == null,
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                )
                PickerTile(
                    icon = Icons.Rounded.Schedule,
                    label = "Time",
                    value = selectedTime?.format(DateTimeFormatter.ofPattern("h:mm a")),
                    placeholder = "Pick time",
                    isError = triedToSave && selectedTime == null,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            SectionLabel("Remind me")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReminderOptions.forEach { mins ->
                    QuickChip(
                        label = TimeLabels.reminderLabel(mins),
                        selected = reminderMinutes == mins
                    ) { reminderMinutes = mins }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SectionLabel("Repeat")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Repeat.entries.forEach { r ->
                    QuickChip(label = r.label, selected = repeat == r) { repeat = r }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showDatePicker) {
            // DatePicker works in UTC midnight millis, so convert with UTC rather than the local zone.
            val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = (selectedDate ?: LocalDate.now())
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                }
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                        if (selectedTime == null) showTimePicker = true
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (showTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = selectedTime?.hour ?: LocalTime.now().plusHours(1).hour,
                initialMinute = selectedTime?.minute ?: 0
            )
            Dialog(onDismissRequest = { showTimePicker = false }) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Select time",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                        )
                        TimePicker(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                                showTimePicker = false
                            }) { Text("OK") }
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm && editMeeting != null && onDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                title = { Text("Delete this meeting?") },
                text = { Text("“${editMeeting.title}” and its reminder will be removed.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete(editMeeting)
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

/** The "just say it" box: free-text field, mic, parse button and example phrases. */
@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    isListening: Boolean,
    parsed: Boolean,
    onParse: () -> Unit,
    onMic: () -> Unit,
    onExample: (String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Just say it",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Coffee with Priya tomorrow at 10am") },
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onParse() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceInputButton(isListening = isListening, onClick = onMic)
                Spacer(modifier = Modifier.width(4.dp))
                AnimatedContent(
                    targetState = parsed,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    },
                    label = "parse_hint",
                    modifier = Modifier.weight(1f)
                ) { done ->
                    Text(
                        text = if (done) "Filled in below. Check and save." else "Speak or type, then tap →",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (done) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = onParse,
                    enabled = text.trim().length >= 3,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Fill in details")
                }
            }
        }
    }

    AnimatedVisibility(
        visible = text.isEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExamplePhrases.forEach { phrase ->
                SuggestionChip(
                    onClick = { onExample(phrase) },
                    label = { Text(phrase, style = MaterialTheme.typography.bodySmall) },
                    shape = MaterialTheme.shapes.small,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp, bottom = 10.dp)
            .semantics { heading() }
    )
}

/** iCalendar RRULE for the calendar app's "repeat" field. */
private fun repeatRule(repeat: Repeat): String? = when (repeat) {
    Repeat.NONE -> null
    Repeat.DAILY -> "FREQ=DAILY"
    Repeat.WEEKDAYS -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
    Repeat.WEEKLY -> "FREQ=WEEKLY"
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = MaterialTheme.shapes.small,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun PickerTile(
    icon: ImageVector,
    label: String,
    value: String?,
    placeholder: String,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        when {
            isError -> MaterialTheme.colorScheme.error
            value != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "tile_border"
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                },
                label = "tile_value"
            ) { v ->
                Text(
                    text = v ?: placeholder,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        isError -> MaterialTheme.colorScheme.error
                        v == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
