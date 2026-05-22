package com.dosetap.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetap.DoseTapApp
import com.dosetap.R
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.ui.paywall.PaywallBottomSheet
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
    val isPro by container.isPro.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPaywall by remember { mutableStateOf(false) }
    var showBackdateDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showMedSwitcher by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar(uiState.errorMessage!!)
            viewModel.clearError()
        }
    }

    // Dose warning dialog
    uiState.pendingWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelLogDose() },
            icon = {
                Text(
                    text = when (warning.type) {
                        DoseWarningType.DOUBLE_DOSE -> "⚠️"
                        DoseWarningType.LATE_DOSE -> "🌙"
                        DoseWarningType.OFF_DAY -> "📅"
                    },
                    fontSize = 28.sp,
                )
            },
            title = {
                Text(
                    text = when (warning.type) {
                        DoseWarningType.DOUBLE_DOSE -> "Double dose?"
                        DoseWarningType.LATE_DOSE -> "Too late?"
                        DoseWarningType.OFF_DAY -> "Off-day today"
                    },
                    fontWeight = FontWeight.Bold,
                )
            },
            text = { Text(warning.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmLogDose() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
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
                    val hasManyMeds = uiState.medications.size > 1
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (hasManyMeds) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                        ) { showMedSwitcher = true } else Modifier,
                    ) {
                        Text(
                            text = uiState.medication?.name ?: "DoseTap",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasManyMeds) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Switch medication",
                                modifier = Modifier.size(16.dp).padding(start = 2.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    if (!isPro) {
                        TextButton(
                            onClick = { showPaywall = true },
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFF57F17),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Pro",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFF57F17),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Stats")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.History, contentDescription = "History")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { showOverflowMenu = false; onNavigateToSettings() },
                            )
                            DropdownMenuItem(
                                text = { Text("Log for earlier time") },
                                leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    if (uiState.medication != null) showBackdateDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (uiState.tomorrowSkipped) "Undo skip tomorrow" else "Skip tomorrow")
                                },
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    if (uiState.tomorrowSkipped) viewModel.clearSkipTomorrow()
                                    else viewModel.skipTomorrow()
                                },
                            )
                        }
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
            // Critical banners — shown only when something needs action
            if (uiState.showNotificationBanner) {
                item {
                    ActionBanner(
                        emoji = "🔔",
                        message = "Notifications are off — you won't get reminders",
                        actionLabel = "Fix",
                        onAction = { BatteryOptimizationHelper.openNotificationSettings(context) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            if (uiState.showAlarmMissingBanner) {
                item {
                    ActionBanner(
                        emoji = "⚡",
                        message = "Alarms may have been stopped",
                        actionLabel = "Reschedule",
                        onAction = { viewModel.rescheduleAlarms() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            if (uiState.showTimezoneChangedBanner) {
                item {
                    ActionBanner(
                        emoji = "🕐",
                        message = "Time zone changed. Alarms still set to original times.",
                        actionLabel = "OK",
                        onAction = { viewModel.dismissTimezoneChange() },
                        isWarning = false,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // Main hero dose section
            item {
                DoseHeroSection(
                    uiState = uiState,
                    isPro = isPro,
                    onLogDose = { uiState.medication?.let { viewModel.requestLogDose(it.id) } },
                    onNavigateToSettings = onNavigateToSettings,
                    onShowPaywall = { showPaywall = true },
                )
            }

            // Undo banner
            val doseTime = uiState.lastDoseTimeMillis
            if (uiState.showUndoBanner && doseTime != null) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically() + fadeIn(),
                    ) {
                        UndoBanner(
                            doseTimeMillis = doseTime,
                            onUndo = { viewModel.undoLastDose() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Dose notes card (appears briefly after logging)
            val noteForTapId = uiState.pendingNoteForTapId
            if (noteForTapId != null && !uiState.showUndoBanner) {
                item {
                    DoseNotesCard(
                        onSave = { note, tags -> viewModel.saveNote(noteForTapId, note, tags) },
                        onDismiss = { viewModel.dismissNoteCard() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // Refill warning
            val pillsRemaining = uiState.pillsRemaining
            if (uiState.showRefillWarning && pillsRemaining != null) {
                item {
                    RefillBanner(
                        pillsRemaining = pillsRemaining,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Today's timeline — minimal, only if there are events
            if (uiState.todayEvents.isNotEmpty()) {
                item {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                items(uiState.todayEvents.size) { index ->
                    DoseEventRow(
                        event = uiState.todayEvents[index],
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }

    if (showPaywall) {
        PaywallBottomSheet(
            price = container.billingManager.formattedPrice(),
            onPurchase = { activity -> container.billingManager.launchPurchase(activity) },
            onRestore = { container.billingManager.restorePurchases() },
            onGiftCode = { code ->
                if (container.giftCodeManager.isValid(code)) {
                    viewModel.unlockViaGiftCode()
                    true
                } else false
            },
            onDismiss = { showPaywall = false },
        )
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

    if (showMedSwitcher && uiState.medications.size > 1) {
        ModalBottomSheet(
            onDismissRequest = { showMedSwitcher = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "Switch medication",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                uiState.medications.forEach { med ->
                    val isSelected = med.id == uiState.medication?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = LocalIndication.current,
                            ) {
                                viewModel.selectMedication(med.id)
                                showMedSwitcher = false
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("💊", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            med.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DoseHeroSection(
    uiState: HomeUiState,
    isPro: Boolean,
    onLogDose: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowPaywall: () -> Unit,
) {
    val context = LocalContext.current

    val (statusEmoji, statusText, statusColor) = when {
        uiState.isOffDay -> Triple("📅", "Off-day today", MaterialTheme.colorScheme.secondary)
        uiState.effectWindowUntilMillis != null ->
            Triple("⚡", "Active until ${TimeFormatter.formatTime(context, uiState.effectWindowUntilMillis)}", Color(0xFF1565C0))
        uiState.todayEvents.any { it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN } ->
            Triple("✅", "Dose logged today", Color(0xFF2E7D32))
        else -> Triple("💊", "No dose logged yet", MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (uiState.medication != null) {
            // Status chip
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = statusEmoji, fontSize = 14.sp)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Elapsed time + next-dose-available countdown
            val elapsed = uiState.elapsedSinceLastDoseMillis
            val nextAvailable = uiState.nextDoseAvailableAtMillis
            if (elapsed != null && elapsed > 0) {
                Text(
                    text = TimeFormatter.formatElapsedShort(elapsed) + " ago",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val lastDoseEvent = uiState.todayEvents.lastOrNull {
                    it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN
                }
                if (lastDoseEvent != null) {
                    Text(
                        text = "at ${TimeFormatter.formatTime(context, lastDoseEvent.actionedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (nextAvailable != null && nextAvailable > System.currentTimeMillis()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Next dose at ${TimeFormatter.formatTime(context, nextAvailable)} — " +
                            TimeFormatter.formatCountdown(nextAvailable - System.currentTimeMillis()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1565C0),
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else if (uiState.todayEvents.isEmpty()) {
                Text(
                    text = "No dose logged today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Big dose button — 250ms press-and-hold phantom tap protection
            var holdProgress by remember { mutableFloatStateOf(0f) }
            var isHolding by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (isHolding) 0.93f else 1f,
                animationSpec = tween(100),
                label = "button_scale",
            )

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                val startTime = System.currentTimeMillis()
                                val holdDuration = 250L
                                val step = 16L
                                var elapsed2 = 0L
                                var confirmed = false
                                try {
                                    while (elapsed2 < holdDuration) {
                                        kotlinx.coroutines.delay(step)
                                        elapsed2 = System.currentTimeMillis() - startTime
                                        holdProgress = (elapsed2.toFloat() / holdDuration).coerceIn(0f, 1f)
                                        if (!tryAwaitRelease()) {
                                            // Released early
                                            break
                                        }
                                    }
                                    if (elapsed2 >= holdDuration) {
                                        confirmed = true
                                        onLogDose()
                                    }
                                } finally {
                                    isHolding = false
                                    holdProgress = 0f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Radial progress fill during hold
                if (isHolding && holdProgress > 0f) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(160.dp)) {
                        drawArc(
                            color = android.graphics.Color.WHITE.let {
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f)
                            },
                            startAngle = -90f,
                            sweepAngle = 360f * holdProgress,
                            useCenter = true,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = "💊", fontSize = 42.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isHolding) "Hold…" else "Log dose",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Off-day note + override
            if (uiState.isOffDay) {
                TextButton(onClick = onLogDose) {
                    Text(
                        "Log anyway (off-day)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (uiState.tomorrowSkipped) {
                Text(
                    text = "📅 Tomorrow is skipped",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        } else {
            // No medication configured
            Spacer(Modifier.height(40.dp))
            Text(text = "💊", fontSize = 56.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Set up your medication",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add your medication name and follow-up reminders to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNavigateToSettings,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Text("Add Medication", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DoseEventRow(event: DoseEvent, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (emoji, label, tint) = when (event.action) {
        DoseAction.INITIAL_TAP -> Triple("💊", "Dose logged", MaterialTheme.colorScheme.primary)
        DoseAction.TAKEN -> Triple("✅", "Taken", MaterialTheme.colorScheme.primary)
        DoseAction.SNOOZED -> Triple("💤", "Snoozed", MaterialTheme.colorScheme.secondary)
        DoseAction.SKIPPED -> Triple("⏭", "Skipped", MaterialTheme.colorScheme.outline)
        DoseAction.MISSED -> Triple("❌", "Missed", MaterialTheme.colorScheme.error)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = TimeFormatter.formatTime(context, event.actionedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionBanner(
    emoji: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    isWarning: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isWarning)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    val textColor = if (isWarning)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun RefillBanner(pillsRemaining: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF3E0))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💊", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$pillsRemaining pill${if (pillsRemaining == 1) "" else "s"} left — time to refill",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF5D4037),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UndoBanner(doseTimeMillis: Long, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✅", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Logged at ${TimeFormatter.formatTime(context, doseTimeMillis)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUndo) {
            Text(
                text = "Undo",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseNotesCard(
    onSave: (note: String?, tags: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presetTags = listOf("with food", "late", "forgot earlier", "half dose", "brand change", "crashed early", "slept poorly")
    val selectedTags = remember { mutableStateOf(setOf<String>()) }
    var customNote by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Add a note?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Cancel, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presetTags.forEach { tag ->
                    val isSelected = tag in selectedTags.value
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTags.value = if (isSelected)
                                selectedTags.value - tag
                            else
                                selectedTags.value + tag
                        },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customNote,
                onValueChange = { customNote = it },
                placeholder = { Text("Optional note…", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Skip") }
                TextButton(onClick = {
                    val tagsStr = selectedTags.value.takeIf { it.isNotEmpty() }?.joinToString(",")
                    onSave(customNote.trim().takeIf { it.isNotBlank() }, tagsStr)
                }) { Text("Save", fontWeight = FontWeight.SemiBold) }
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
                    text = "What time did you take it?",
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
                        onConfirm(minOf(cal.timeInMillis, System.currentTimeMillis()))
                    }) { Text("Log") }
                }
            }
        }
    }
}
