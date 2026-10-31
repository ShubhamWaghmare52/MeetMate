package com.meetmate.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.meetmate.app.data.Meeting
import com.meetmate.app.notification.NotificationHelper
import com.meetmate.app.ui.screens.AddMeetingScreen
import com.meetmate.app.ui.screens.HomeScreen
import com.meetmate.app.ui.theme.MeetMateTheme
import com.meetmate.app.viewmodel.MeetingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeetMateTheme {
                MeetMateNavigation()
            }
        }
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
fun MeetMateNavigation() {
    val navController = rememberNavController()
    val viewModel: MeetingViewModel = viewModel()
    val meetings by viewModel.allMeetings.collectAsStateWithLifecycle()

    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermission = rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS
        )
        LaunchedEffect(Unit) {
            if (!notificationPermission.status.isGranted) {
                notificationPermission.launchPermissionRequest()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!NotificationHelper.canScheduleAlarms(context)) {
            context.startActivity(NotificationHelper.getExactAlarmSettingsIntent())
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize()
    ) {
        composable(
            "home",
            enterTransition = {
                fadeIn(spring(stiffness = Spring.StiffnessLow))
            },
            exitTransition = {
                fadeOut(spring(stiffness = Spring.StiffnessLow))
            }
        ) {
            HomeScreen(
                meetings = meetings,
                onAddClick = { navController.navigate("add") },
                onEditMeeting = { meeting ->
                    navController.navigate("edit/${meeting.id}")
                },
                onDeleteMeeting = { viewModel.deleteMeeting(it) },
                onCompleteMeeting = { viewModel.completeMeeting(it) },
                onRestoreMeeting = { viewModel.restoreMeeting(it) }
            )
        }

        composable(
            "add",
            enterTransition = { sheetEnter() },
            exitTransition = { sheetExit() }
        ) {
            AddMeetingScreen(
                onSave = { title, person, dateTimeMillis, reminderMinutes ->
                    viewModel.addMeeting(title, person, dateTimeMillis, reminderMinutes)
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
            var loaded by remember { mutableStateOf(false) }

            LaunchedEffect(meetingId) {
                viewModel.getMeetingById(meetingId) {
                    meeting = it
                    loaded = true
                }
            }

            if (loaded && meeting != null) {
                AddMeetingScreen(
                    editMeeting = meeting,
                    onSave = { title, person, dateTimeMillis, reminderMinutes ->
                        viewModel.updateMeeting(
                            meeting!!, title, person, dateTimeMillis, reminderMinutes
                        )
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
}
