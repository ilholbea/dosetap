package com.dosetap.ui.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetap.DoseTapApp
import com.dosetap.R
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.util.BatteryOptimizationHelper
import com.dosetap.util.OemDetector
import com.dosetap.util.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container

    val viewModel: HomeViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(container, context) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showHelp by remember { mutableStateOf(false) }
    var showBackdateDialog by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar(uiState.errorMessage!!)
            viewModel.clearError()
        }
    }

    // Warning dialog — shown before logging if double-dose, late, or off-day
    uiState.pendingWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelLogDose() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    text = when (warning.type) {
                        DoseWarningType.DOUBLE_DOSE -> "Possible double dose"
                        DoseWarningType.LATE_DOSE -> "Too late for this medication"
                        DoseWarningType.OFF_DAY -> "Today is an off-day"
                    }
                )
            },
            text = { Text(warning.message) },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { viewModel.confirmLogDose() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    )
                ) { Text("Log anyway") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLogDose() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            Icons.Filled.HelpOutline,
                            contentDescription = "How it works",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onNavigateToStats) {
                        Icon(
                            Icons.Filled.BarChart,
                            contentDescription = "Adherence stats",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.cd_history_button)
                        }
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.cd_settings_button)
                        }
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Alert banners
            if (uiState.showNotificationBanner) {
                item {
                    AlertBanner(
                        message = stringResource(R.string.home_notification_denied_banner),
                        actionLabel = stringResource(R.string.home_notification_open_settings),
                        onAction = { BatteryOptimizationHelper.openNotificationSettings(context) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            if (uiState.showAlarmMissingBanner) {
                item {
                    AlertBanner(
                        message = stringResource(R.string.home_alarm_missing_banner),
                        actionLabel = stringResource(R.string.home_alarm_reschedule),
                        onAction = { viewModel.rescheduleAlarms() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            if (uiState.showReliabilityBanner && OemDetector.isKnownProblematic()) {
                item {
                    AlertBanner(
                        message = stringResource(R.string.home_reliability_warning),
                        actionLabel = stringResource(R.string.home_fix_reliability),
                        onAction = { BatteryOptimizationHelper.openOemSettings(context) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            if (uiState.showTimezoneChangedBanner) {
                item {
                    InfoBanner(
                        message = stringResource(R.string.home_timezone_changed),
                        actionLabel = stringResource(R.string.home_timezone_dismiss),
                        onAction = { viewModel.dismissTimezoneChange() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Off-day banner
            if (uiState.isOffDay) {
                item {
                    InfoBanner(
                        message = "Today is a scheduled off-day for ${uiState.medication?.name ?: "your medication"}.",
                        actionLabel = "Log anyway",
                        onAction = {
                            uiState.medication?.let { viewModel.requestLogDose(it.id) }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Hero dose button
            item {
                DoseHeroSection(
                    medication = uiState.medication,
                    isLoading = uiState.isLoading,
                    effectWindowUntilMillis = uiState.effectWindowUntilMillis,
                    onLogDose = {
                        uiState.medication?.let { viewModel.requestLogDose(it.id) }
                    },
                    onNavigateToSettings = onNavigateToSettings,
                    onBackdate = { showBackdateDialog = true },
                )
            }

            // Pill count / refill warning card
            if (uiState.showRefillWarning && uiState.pillsRemaining != null) {
                item {
                    RefillWarningCard(
                        pillsRemaining = uiState.pillsRemaining,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Undo banner
            val doseTime = uiState.lastDoseTimeMillis
            if (uiState.showUndoBanner && doseTime != null) {
                item {
                    UndoBanner(
                        doseTimeMillis = doseTime,
                        onUndo = { viewModel.undoLastDose() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Today's timeline
            if (uiState.todayEvents.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                items(uiState.todayEvents) { event ->
                    DoseEventRow(
                        event = event,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    if (showHelp) {
        ModalBottomSheet(
            onDismissRequest = { showHelp = false },
            sheetState = bottomSheetState,
        ) {
            HowItWorksContent(onDismiss = { showHelp = false })
        }
    }

    if (showBackdateDialog) {
        uiState.medication?.let { med ->
            BackdateDialog(
                onConfirm = { timeMillis ->
                    showBackdateDialog = false
                    viewModel.logDoseAt(med.id, timeMillis)
                },
                onDismiss = { showBackdateDialog = false },
            )
        }
    }
}

@Composable
private fun DoseHeroSection(
    medication: com.dosetap.domain.model.Medication?,
    isLoading: Boolean,
    effectWindowUntilMillis: Long?,
    onLogDose: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onBackdate: () -> Unit,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .padding(top = 32.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (medication != null) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

                // Effect window indicator
                if (effectWindowUntilMillis != null) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Active until ${TimeFormatter.formatTime(context, effectWindowUntilMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Large circular dose button
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(primary)
                        .clickable(onClick = onLogDose),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Medication,
                            contentDescription = stringResource(R.string.home_log_dose),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_log_dose),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.home_tap_to_record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onBackdate,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Log for earlier time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!isLoading) {
                Icon(
                    imageVector = Icons.Filled.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.home_no_medication),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onNavigateToSettings) {
                    Text(stringResource(R.string.home_add_medication))
                }
            }
        }
    }
}

@Composable
private fun DoseEventRow(event: DoseEvent, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (icon, tint, label) = when (event.action) {
        DoseAction.INITIAL_TAP -> Triple(Icons.Filled.Medication, MaterialTheme.colorScheme.primary, stringResource(R.string.dose_action_initial_tap))
        DoseAction.TAKEN -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, stringResource(R.string.dose_action_taken))
        DoseAction.SNOOZED -> Triple(Icons.Filled.Schedule, MaterialTheme.colorScheme.secondary, stringResource(R.string.dose_action_snoozed))
        DoseAction.SKIPPED -> Triple(Icons.Outlined.Cancel, MaterialTheme.colorScheme.outline, stringResource(R.string.dose_action_skipped))
        DoseAction.MISSED -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, stringResource(R.string.dose_action_missed))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.cd_dose_status_icon, label),
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = TimeFormatter.formatTime(context, event.actionedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AlertBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun InfoBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun UndoBanner(doseTimeMillis: Long, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(
                    R.string.widget_logged_at,
                    TimeFormatter.formatTime(context, doseTimeMillis),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            TextButton(onClick = onUndo) {
                Text(
                    text = stringResource(R.string.action_undo),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun RefillWarningCard(pillsRemaining: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = "Refill reminder",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "$pillsRemaining pill${if (pillsRemaining == 1) "" else "s"} remaining — time to refill your prescription.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackdateDialog(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = java.util.Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = now.get(java.util.Calendar.HOUR_OF_DAY),
        initialMinute = now.get(java.util.Calendar.MINUTE),
    )
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Log for earlier time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                androidx.compose.material3.TimePicker(state = timePickerState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = {
                        val cal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(java.util.Calendar.MINUTE, timePickerState.minute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        // Don't allow future times
                        val selectedMillis = minOf(cal.timeInMillis, System.currentTimeMillis())
                        onConfirm(selectedMillis)
                    }) { Text("Log") }
                }
            }
        }
    }
}

@Composable
private fun HowItWorksContent(onDismiss: () -> Unit) {
    val steps = listOf(
        Triple(Icons.Filled.Medication, "Log a dose", "Tap the big button or the widget any time you take your medication. Follow-up reminders fire automatically."),
        Triple(Icons.Filled.CheckCircle, "Undo in 10 seconds", "Made a mistake? Tap \"Undo\" on the green banner right after logging to cancel."),
        Triple(Icons.Filled.Warning, "Smart warnings", "DoseTap warns you before logging a double dose, taking medication past your \"too late\" cutoff, or on off-days."),
        Triple(Icons.Filled.Schedule, "Effect window", "Set your medication's duration in Settings — the home screen shows \"Active until HH:mm\" so you always know when the dose wears off."),
        Triple(Icons.Filled.Refresh, "Backdate a dose", "Forgot to log earlier? Tap \"Log for earlier time\" to record the correct time."),
        Triple(Icons.Filled.BarChart, "Adherence stats", "Tap the chart icon for streaks, 7-day / 30-day adherence, and a 14-day visual chart."),
        Triple(Icons.Filled.History, "Dose history", "Full history with filter by Taken / Snoozed / Skipped / Missed. Swipe left to delete any entry."),
        Triple(Icons.Filled.Settings, "Settings", "Edit medication name, follow-up intervals, alarm tone, daily reminder, off-days, pill count, and more."),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = "How DoseTap works",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "A simple guide to get the most out of the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        steps.forEachIndexed { index, (icon, title, description) ->
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (index < steps.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Got it")
        }
    }
}
