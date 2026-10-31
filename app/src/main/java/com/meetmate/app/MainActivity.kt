package com.meetmate.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.meetmate.app.data.AppSettings
import com.meetmate.app.data.Meeting
import com.meetmate.app.data.ThemeMode
import com.meetmate.app.notification.AlarmService
import com.meetmate.app.notification.NotificationHelper
import com.meetmate.app.ui.components.ReminderIssue
import com.meetmate.app.ui.components.SettingsSheet
import com.meetmate.app.ui.screens.AddMeetingScreen
import com.meetmate.app.ui.screens.HomeScreen
import com.meetmate.app.ui.theme.MeetMateTheme
import com.meetmate.app.viewmodel.MeetingViewModel
import com.meetmate.app.widget.NextMeetingWidget

class MainActivity : ComponentActivity() {

    /** A route requested from outside the UI: an app shortcut, the Quick Settings tile or the widget. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            var themeMode by remember { mutableStateOf(AppSettings.themeMode(this)) }
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status/navigation bar icons follow the in-app theme, not just the system one.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                )
                onDispose {}
            }

            MeetMateTheme(darkTheme = dark) {
                MeetMateNavigation(
                    pendingRoute = pendingRoute,
                    onRouteHandled = { pendingRoute = null },
                    themeMode = themeMode,
                    onThemeChange = {
                        themeMode = it
                        AppSettings.setThemeMode(this, it)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP_ALARM -> AlarmService.dismiss(this)
            ACTION_NEW_MEETING -> pendingRoute = "add?voice=false"
            ACTION_NEW_MEETING_VOICE -> pendingRoute = "add?voice=true"
        }
    }

    companion object {
        const val ACTION_STOP_ALARM = "com.meetmate.app.action.STOP_ALARM"
        const val ACTION_NEW_MEETING = "com.meetmate.app.action.NEW_MEETING"
        const val ACTION_NEW_MEETING_VOICE = "com.meetmate.app.action.NEW_MEETING_VOICE"
    }
}

// Add/edit screens rise a short distance and fade in: no overshoot, so it feels calm rather than bouncy.
private fun sheetEnter(): EnterTransition =
    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 12 } +
        fadeIn(tween(250))

private fun sheetExit(): ExitTransition =
    slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it / 12 } +
        fadeOut(tween(180))

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MeetMateNavigation(
    pendingRoute: String?,
    onRouteHandled: () -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val viewModel: MeetingViewModel = viewModel()
    val meetings by viewModel.allMeetings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var issues by remember { mutableStateOf(ReminderIssue.current(context)) }
    var showSettings by remember { mutableStateOf(false) }
    // Permissions can change in system settings while we're away, so re-check on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { issues = ReminderIssue.current(context) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) {
            issues = ReminderIssue.current(context)
        }
        LaunchedEffect(Unit) {
            if (!notificationPermission.status.isGranted) notificationPermission.launchPermissionRequest()
        }
    }

    LaunchedEffect(meetings) { NextMeetingWidget.updateAll(context) }

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let {
            navController.navigate(it) { launchSingleTop = true }
            onRouteHandled()
        }
    }

    fun fixIssue(issue: ReminderIssue) {
        val intent = when (issue) {
            ReminderIssue.NOTIFICATIONS -> NotificationHelper.notificationSettingsIntent(context)
            ReminderIssue.EXACT_ALARMS -> NotificationHelper.exactAlarmSettingsIntent(context)
            ReminderIssue.FULL_SCREEN -> NotificationHelper.fullScreenSettingsIntent(context)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(NotificationHelper.notificationSettingsIntent(context))
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize()
    ) {
        composable(
            "home",
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            HomeScreen(
                meetings = meetings,
                issues = issues,
                onFixIssue = ::fixIssue,
                onSettingsClick = { showSettings = true },
                onAddClick = { navController.navigate("add?voice=false") },
                onEditMeeting = { meeting -> navController.navigate("edit/${meeting.id}") },
                onDeleteMeeting = { viewModel.deleteMeeting(it) },
                onCompleteMeeting = { viewModel.completeMeeting(it) },
                onRestoreMeeting = { viewModel.restoreMeeting(it) }
            )
        }

        composable(
            "add?voice={voice}",
            arguments = listOf(navArgument("voice") { type = NavType.BoolType; defaultValue = false }),
            enterTransition = { sheetEnter() },
            exitTransition = { sheetExit() }
        ) { backStackEntry ->
            AddMeetingScreen(
                startWithVoice = backStackEntry.arguments?.getBoolean("voice") == true,
                onSave = { draft ->
                    viewModel.addMeeting(draft)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            "edit/{meetingId}",
            arguments = listOf(navArgument("meetingId") { type = NavType.LongType }),
            enterTransition = { sheetEnter() },
            exitTransition = { sheetExit() }
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getLong("meetingId") ?: return@composable
            var meeting by remember { mutableStateOf<Meeting?>(null) }

            LaunchedEffect(meetingId) {
                viewModel.getMeetingById(meetingId) { meeting = it }
            }

            meeting?.let { current ->
                AddMeetingScreen(
                    editMeeting = current,
                    onSave = { draft ->
                        viewModel.updateMeeting(current, draft)
                        navController.popBackStack()
                    },
                    onDelete = { m ->
                        viewModel.deleteMeeting(m)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            issues = issues,
            onFixIssue = ::fixIssue,
            onDismiss = { showSettings = false }
        )
    }
}
