package com.dosetap.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
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
import com.dosetap.domain.model.FeatureFlag
import com.dosetap.domain.model.Medication
import com.dosetap.ui.paywall.PaywallBottomSheet
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dosetap.util.BatteryOptimizationHelper
import com.dosetap.util.ExactAlarmHelper
import com.dosetap.util.OemDetector
import com.dosetap.util.TimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToFocus: () -> Unit = {},
    onStartFocusTimer: () -> Unit = {},
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
    val celebrationsEnabled by container.settingsRepository.celebrationsEnabled.collectAsStateWithLifecycle(true)
    val snackbarHostState = remember { SnackbarHostState() }
    var showPaywall by remember { mutableStateOf(false) }
    var showBackdateDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var showNoteCard by remember { mutableStateOf(false) }
    var confettiBurstKey by remember { mutableIntStateOf(0) }
    var confettiBonus by remember { mutableStateOf(false) }
    var firstDoseMessage by remember { mutableStateOf<String?>(null) }
    var showFirstDoseOverlay by remember { mutableStateOf(false) }
    // Exact-alarm permission (Android 13+ must grant it). Re-checked on resume so the banner
    // clears the moment the user returns from the system settings screen (E-H3).
    var canExactAlarm by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canExactAlarm = ExactAlarmHelper.canScheduleExact(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // System reduced-motion: skip full-screen particle bursts (sensory-overload safe — row #11).
    val reducedMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }
    var showBrainDump by remember { mutableStateOf(false) }
    var showRoulette by remember { mutableStateOf(false) }
    var showBreathe by remember { mutableStateOf(false) }
    var showQuicklist by remember { mutableStateOf(false) }
    val brainDumpItems by container.settingsRepository.brainDumpItems.collectAsStateWithLifecycle(emptyList())
    val quicklistItems by container.settingsRepository.quicklistItems.collectAsStateWithLifecycle(emptyList())

    // Collect one-shot celebration events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.celebrationEvent.collect { event ->
            if (celebrationsEnabled) {
                confettiBonus = event.bonus
                // Reduced motion → no particles; the first-dose card still shows as a static confirm.
                if (!reducedMotion) confettiBurstKey++
                if (event.kind == CelebrationKind.FIRST_OF_DAY) {
                    firstDoseMessage = event.message
                    showFirstDoseOverlay = true
                }
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar(uiState.errorMessage!!)
            viewModel.clearError()
        }
    }

    // Auto-show note card when a new dose is logged
    LaunchedEffect(uiState.pendingNoteForTapId) {
        if (uiState.pendingNoteForTapId == null) showNoteCard = false
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

    // Trial expired one-time dialog
    if (uiState.showTrialExpiredOnce) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTrialExpired() },
            icon = { Text("⭐", fontSize = 28.sp) },
            title = { Text("Pro trial ended", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your 14-day trial has ended. Your dose history is safe and all free features still work.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = { showPaywall = true; viewModel.dismissTrialExpired() }) {
                    Text("Unlock Pro")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTrialExpired() }) { Text("Continue free") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "DopaTap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    if (!isPro) {
                        TextButton(onClick = { showPaywall = true }) {
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
                                text = { Text(if (uiState.isPaused) "Resume reminders" else "Pause reminders") },
                                leadingIcon = { Icon(if (uiState.isPaused) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    if (uiState.isPaused) viewModel.resumeReminders() else showPauseDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Help & tips") },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { showOverflowMenu = false; showHelpSheet = true },
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
            // Trial ending soon banner (last 3 days)
            val daysLeft = uiState.trialDaysRemaining
            if (isPro && daysLeft != null && daysLeft in 0..3) {
                item {
                    ActionBanner(
                        emoji = "⭐",
                        message = if (daysLeft == 0) "Pro trial ends today" else "Pro trial ends in $daysLeft day${if (daysLeft == 1) "" else "s"}",
                        actionLabel = "Unlock",
                        onAction = { showPaywall = true },
                        isWarning = false,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // Medication selector — always shown for Pro (reveals + add button)
            if (uiState.medications.size > 1 || isPro) {
                item {
                    MedicationSelector(
                        medications = uiState.medications,
                        selectedId = uiState.medication?.id,
                        onSelect = { viewModel.selectMedication(it) },
                        onAdd = if (isPro) onNavigateToSettings else null,
                    )
                }
            }

            // Paused reminders banner
            if (uiState.isPaused) {
                item {
                    PausedBanner(
                        pausedUntilMillis = uiState.pausedUntilMillis,
                        onResume = { viewModel.resumeReminders() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // Critical banners
            if (!canExactAlarm) {
                item {
                    ActionBanner(
                        emoji = "⏰",
                        message = "Allow exact alarms so reminders fire on time",
                        actionLabel = "Allow",
                        onAction = { ExactAlarmHelper.openExactAlarmSettings(context) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
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
                    confettiBurstKey = confettiBurstKey,
                    confettiBonus = confettiBonus,
                    celebrationsEnabled = celebrationsEnabled,
                    onLogDose = { uiState.medication?.let { viewModel.requestLogDose(it.id) } },
                    onNavigateToSettings = onNavigateToSettings,
                    onShowPaywall = { showPaywall = true },
                    onSkipTomorrow = { viewModel.skipTomorrow() },
                    onClearSkipTomorrow = { viewModel.clearSkipTomorrow() },
                )
            }

            // Undo banner with "+ Add note" link
            val doseTime = uiState.lastTapTimeMillis
            if (uiState.showUndoBanner && doseTime != null) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically() + fadeIn(),
                    ) {
                        UndoBanner(
                            doseTimeMillis = doseTime,
                            onUndo = { viewModel.undoLastDose() },
                            onAddNote = {
                                if (uiState.pendingNoteForTapId != null) showNoteCard = true
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Dose notes card — only shown when user taps "+ Add note"
            val noteForTapId = uiState.pendingNoteForTapId
            if (showNoteCard && noteForTapId != null) {
                item {
                    DoseNotesCard(
                        onSave = { note, tags ->
                            viewModel.saveNote(noteForTapId, note, tags)
                            showNoteCard = false
                        },
                        onDismiss = {
                            viewModel.dismissNoteCard()
                            showNoteCard = false
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // Today's timeline — right below the dose button where it belongs
            if (uiState.todayEvents.isNotEmpty()) {
                item {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
                items(uiState.todayEvents.size) { index ->
                    DoseEventRow(
                        event = uiState.todayEvents[index],
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            } else if (uiState.medication != null && uiState.todayEvents.isEmpty()) {
                item {
                    val hourNow = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    if (hourNow >= 20) {
                        Text(
                            text = "Nothing logged today. Did you forget? Use \"Log for earlier time\" in the ··· menu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // 🧰 Quick tools row (Pro)
            item {
                val toolsUnlocked = FeatureFlag.canUseAdhdTools(isPro)
                ToolsRow(
                    onOpenFocus = if (toolsUnlocked) onNavigateToFocus else ({ showPaywall = true }),
                    onOpenBrainDump = if (toolsUnlocked) { { showBrainDump = true } } else { { showPaywall = true } },
                    onOpenRoulette = if (toolsUnlocked) { { showRoulette = true } } else { { showPaywall = true } },
                    onOpenBreathe = if (toolsUnlocked) { { showBreathe = true } } else { { showPaywall = true } },
                    onOpenQuicklist = if (toolsUnlocked) { { showQuicklist = true } } else { { showPaywall = true } },
                    quicklistCount = if (toolsUnlocked) quicklistItems.count { !it.second } else 0,
                    isPro = toolsUnlocked,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
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

            // Prescription-based refill prediction (Pro)
            val refillByMillis = uiState.rxRefillByDateMillis
            if (isPro && refillByMillis != null) {
                item {
                    var rxBannerDismissed by remember(refillByMillis) { mutableStateOf(false) }
                    if (!rxBannerDismissed) {
                        RxRefillBanner(
                            refillByMillis = refillByMillis,
                            onDismiss = { rxBannerDismissed = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
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
                when (val result = container.giftCodeManager.validate(code)) {
                    com.dosetap.billing.GiftCodeManager.CodeResult.DevGrant -> { viewModel.redeemDevGrant(); result }
                    com.dosetap.billing.GiftCodeManager.CodeResult.PermanentGift -> { viewModel.redeemPermanentGift(); result }
                    com.dosetap.billing.GiftCodeManager.CodeResult.SponsorGift -> { viewModel.startTrial("gift"); result }
                    com.dosetap.billing.GiftCodeManager.CodeResult.Invalid -> null
                }
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

    if (showHelpSheet) {
        HelpSheet(onDismiss = { showHelpSheet = false })
    }

    if (showFirstDoseOverlay && celebrationsEnabled) {
        FirstDoseOverlay(
            message = firstDoseMessage,
            bonus = confettiBonus,
            onDismiss = { showFirstDoseOverlay = false },
        )
    }

    if (showPauseDialog) {
        PauseRemindersDialog(
            onPause = { hours -> viewModel.pauseReminders(hours); showPauseDialog = false },
            onDismiss = { showPauseDialog = false },
        )
    }

    if (showBrainDump) {
        BrainDumpSheet(
            items = brainDumpItems,
            onCapture = { viewModel.addBrainDumpItem(it) },
            onPromote = { viewModel.promoteBrainDumpItem(it) },
            onClear = { viewModel.clearBrainDump() },
            onDismiss = { showBrainDump = false },
        )
    }

    if (showRoulette) {
        TaskRouletteSheet(
            items = brainDumpItems,
            onRemove = { viewModel.removeBrainDumpItem(it) },
            onStartTimer = { showRoulette = false; onStartFocusTimer() },
            onDismiss = { showRoulette = false },
        )
    }

    if (showBreathe) {
        BreatheSheet(onDismiss = { showBreathe = false })
    }

    if (showQuicklist) {
        QuicklistSheet(
            items = quicklistItems,
            onAdd = { viewModel.addQuickItem(it) },
            onToggle = { viewModel.toggleQuickItem(it) },
            onClearDone = { viewModel.clearDoneQuickItems() },
            onDismiss = { showQuicklist = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationSelector(
    medications: List<Medication>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when {
                medications.isEmpty() -> { /* nothing */ }
                medications.size == 1 -> {
                    // Single med: show as a non-interactive selected chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            medications[0].name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                medications.size <= 3 -> {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        medications.forEachIndexed { index, med ->
                            SegmentedButton(
                                selected = med.id == selectedId,
                                onClick = { onSelect(med.id) },
                                shape = SegmentedButtonDefaults.itemShape(index, medications.size),
                                icon = {},
                            ) {
                                Text(
                                    med.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (med.id == selectedId) FontWeight.SemiBold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                else -> {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(medications, key = { it.id }) { med ->
                            FilterChip(
                                selected = med.id == selectedId,
                                onClick = { onSelect(med.id) },
                                label = {
                                    Text(
                                        med.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        if (onAdd != null) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = onAdd,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(36.dp),
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DoseHeroSection(
    uiState: HomeUiState,
    isPro: Boolean,
    confettiBurstKey: Int,
    confettiBonus: Boolean,
    celebrationsEnabled: Boolean,
    onLogDose: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowPaywall: () -> Unit,
    onSkipTomorrow: () -> Unit,
    onClearSkipTomorrow: () -> Unit,
) {
    val context = LocalContext.current
    val rootView = LocalView.current

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val (statusEmoji, statusText, statusColor) = when {
        uiState.isOffDay -> Triple("📅", "Off-day today", secondaryColor)
        uiState.effectWindowUntilMillis != null ->
            Triple("⚡", "Active until ${TimeFormatter.formatTime(context, uiState.effectWindowUntilMillis)}", tertiaryColor)
        uiState.todayEvents.any { it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN } ->
            Triple("✅", "Dose logged today", primaryColor)
        else -> Triple("💊", "No dose logged yet", subtleColor)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (uiState.medication != null) {
            // Status chip — use Material container colors for guaranteed contrast in both themes
            val doseLogged = uiState.todayEvents.any { it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN }
            // secondaryContainer is a muted, readable tonal color — readable in every Material You
            // theme, unlike primaryContainer which dynamic color can make vivid/saturated.
            val chipBg = when {
                uiState.isOffDay -> MaterialTheme.colorScheme.secondaryContainer
                uiState.effectWindowUntilMillis != null -> MaterialTheme.colorScheme.tertiaryContainer
                doseLogged -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val chipFg = when {
                uiState.isOffDay -> MaterialTheme.colorScheme.onSecondaryContainer
                uiState.effectWindowUntilMillis != null -> MaterialTheme.colorScheme.onTertiaryContainer
                doseLogged -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(chipBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = statusEmoji, fontSize = 14.sp)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = chipFg,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Compact elapsed + next dose line
            val elapsed = uiState.elapsedSinceLastDoseMillis
            val nextAvailable = uiState.nextDoseAvailableAtMillis
            val lastDoseEvent = uiState.todayEvents.lastOrNull {
                it.action == DoseAction.INITIAL_TAP || it.action == DoseAction.TAKEN
            }
            if (elapsed != null && elapsed > 0 && lastDoseEvent != null) {
                // Time-blind users can't convert relative↔absolute, so always show both.
                val elapsedText = TimeFormatter.formatElapsedShort(elapsed) + " ago · " +
                    TimeFormatter.formatTime(context, lastDoseEvent.actionedAt)
                val nextText = if (nextAvailable != null && nextAvailable > System.currentTimeMillis())
                    " · next ${TimeFormatter.formatCountdown(nextAvailable - System.currentTimeMillis())}" +
                        " (${TimeFormatter.formatTime(context, nextAvailable)})"
                else ""
                Text(
                    text = "$elapsedText$nextText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = TimeFormatter.formatElapsedShort(elapsed),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
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
            // Shown once the user releases too early, so a quick tap never fails silently (Spec #3).
            var showHoldHint by remember { mutableStateOf(false) }
            val pressScale by animateFloatAsState(
                targetValue = if (isHolding) 0.93f else 1f,
                animationSpec = tween(100),
                label = "button_scale",
            )
            // Subtle breathing animation when dose is due (no dose logged yet today)
            val infiniteTransition = rememberInfiniteTransition(label = "breathe")
            val breathScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.055f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "breath",
            )
            val scale = pressScale * if (uiState.isDueWindow && !isHolding && celebrationsEnabled) breathScale else 1f

            // Outer unclipped Box — confetti lives here so particles spread beyond the button
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Inner clipped button
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
                        .semantics(mergeDescendants = true) {
                            // Accessibility path: TalkBack / switch access log with a plain activate
                            // action, bypassing the hold gesture they can't perform (row #11).
                            contentDescription = "Log dose"
                            onClick(label = "Log dose") { onLogDose(); true }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    showHoldHint = false
                                    // Haptic tick on press-down so the gesture registers immediately.
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        rootView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    } else {
                                        rootView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                    }
                                    val startTime = System.currentTimeMillis()
                                    val holdDuration = 250L
                                    val step = 16L
                                    var elapsed2 = 0L
                                    try {
                                        while (elapsed2 < holdDuration) {
                                            kotlinx.coroutines.delay(step)
                                            elapsed2 = System.currentTimeMillis() - startTime
                                            holdProgress = (elapsed2.toFloat() / holdDuration).coerceIn(0f, 1f)
                                            if (!tryAwaitRelease()) break
                                        }
                                        if (elapsed2 >= holdDuration) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                                rootView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                            } else {
                                                rootView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                            }
                                            onLogDose()
                                        } else {
                                            // Released too early — explain instead of failing silently.
                                            showHoldHint = true
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
                    if (isHolding && holdProgress > 0f) {
                        Canvas(modifier = Modifier.size(160.dp)) {
                            drawArc(
                                color = Color.White.copy(alpha = 0.25f),
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
                            text = if (isHolding) "Hold…" else "Hold to log",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                // Confetti burst — unclipped, spreads beyond button boundary
                if (celebrationsEnabled) {
                    ConfettiBurst(
                        triggerKey = confettiBurstKey,
                        bonus = confettiBonus,
                        modifier = Modifier.size(200.dp),
                    )
                }
            }

            // One-time-per-session explanation for a too-quick tap (Spec #3).
            if (showHoldHint) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Hold for half a second — it stops accidental pocket-logs.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Off-day override
            if (uiState.isOffDay) {
                TextButton(onClick = onLogDose) {
                    Text(
                        "Log anyway (off-day)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            // Skip tomorrow link — visible after today's dose or always
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (uiState.tomorrowSkipped) {
                    Text(
                        text = "📅 Tomorrow is skipped",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearSkipTomorrow) {
                        Text("Undo", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    TextButton(onClick = onSkipTomorrow) {
                        Text(
                            "Skip tomorrow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                text = "Add your medication and DoseTap will remind you 4h and 8h after each dose in case you forget.",
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
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💊", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$pillsRemaining pill${if (pillsRemaining == 1) "" else "s"} left — time to refill",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RxRefillBanner(
    refillByMillis: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateText = remember(refillByMillis) {
        val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
        fmt.format(java.util.Date(refillByMillis))
    }
    val daysUntil = remember(refillByMillis) {
        ((refillByMillis - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt()
    }
    val sublabel = when {
        daysUntil < 0 -> "Refill window — book your appointment soon."
        daysUntil == 0 -> "Refill window — book today."
        daysUntil == 1 -> "Refill window — book by tomorrow ($dateText)."
        else -> "Refill window — book your appointment by $dateText ($daysUntil days)."
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📅", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = sublabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onDismiss) {
            Text("Got it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun UndoBanner(
    doseTimeMillis: Long,
    onUndo: () -> Unit,
    onAddNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✅", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Logged at ${TimeFormatter.formatTime(context, doseTimeMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(
                onClick = onAddNote,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(24.dp),
            ) {
                Text(
                    text = "+ Add note",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
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
    val presetTags = listOf("with food", "late", "half dose")
    val selectedTags = remember { mutableStateOf(setOf<String>()) }
    var customNote by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Add a note",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Cancel, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                "Help & tips",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            HelpSection("Daily use") {
                HelpItem("💊", "Log a dose", "Press and hold the big button, or tap the home-screen widget")
                HelpItem("📅", "Skip tomorrow", "Tap the \"Skip tomorrow\" link under the dose button")
                HelpItem("🕐", "Log earlier time", "Tap ··· menu → Log for earlier time if you forgot to log in the moment")
                HelpItem("🔔", "Lock-screen status", "A quiet notification always shows when you last dosed — no need to open the app")
            }
            HelpSection("Safety") {
                HelpItem("⚠️", "Double-dose warning", "If you log again while the medication may still be active, you'll see a safety warning")
                HelpItem("🌙", "Late cutoff", "Set a time after which taking the med triggers a sleep warning — in Settings → Advanced")
                HelpItem("📅", "Off-days", "Configure days when reminders pause (e.g. weekends) — Pro feature in Settings → Advanced")
            }
            HelpSection("Tracking (Pro)") {
                HelpItem("📊", "90-day heatmap", "See your full adherence calendar in the Stats tab")
                HelpItem("🔥", "Streak", "Consecutive days you've logged a dose — shown in Stats")
                HelpItem("📄", "Doctor report", "One-tap PDF of your adherence for your psychiatrist appointment — in Settings")
            }
            HelpSection("🧰 Tools") {
                HelpItem("🧠", "Focus Timer", "Tap the Focus chip below the dose button. Choose a mode (Focus/Task/Transition/Break), set a duration, and start. A notification fires when it ends — even if the app is closed.")
                HelpItem("💡", "Brain dump", "Tap the Brain dump chip to capture fleeting thoughts instantly. ADHD brains drop tasks constantly — this is your safety net. Review later and turn them into actual tasks.")
                HelpItem("⏱️", "Task mode (count-up)", "Focus → Task mode counts up instead of down. Useful for tracking how long you've been at something without the pressure of a deadline.")
            }
            HelpSection("Reliability") {
                HelpItem("🔋", "Battery exemption", "Exempt DoseTap in Settings → Reliability so Android doesn't kill your alarms")
                HelpItem("🧪", "Test reminder", "Settings → Reliability → Test reminder fires an alarm in 10 seconds to confirm it works")
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    content()
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun HelpItem(emoji: String, title: String, description: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(emoji, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun PausedBanner(
    pausedUntilMillis: Long,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeText = remember(pausedUntilMillis) {
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        fmt.format(java.util.Date(pausedUntilMillis))
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Reminders paused",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Until $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onResume) {
                Text("Resume", color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun ConfettiBurst(triggerKey: Int, bonus: Boolean = false, modifier: Modifier = Modifier) {
    if (triggerKey == 0) return

    var show by remember(triggerKey) { mutableStateOf(true) }
    val progress = remember(triggerKey) { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        progress.animateTo(1f, animationSpec = tween(if (bonus) 1000 else 800, easing = FastOutSlowInEasing))
        show = false
    }

    if (!show) return

    val p = progress.value
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Bonus bursts get an alternate warm-gold palette and denser particles (Spec #4).
        val particleColors = if (bonus) listOf(
            Color(0xFFFFD700), Color(0xFFFFA000), Color(0xFFFF7043),
            Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFFFFFF),
        ) else listOf(
            Color(0xFFFFD700), Color(0xFFFF6B6B), Color(0xFF4ECDC4),
            Color(0xFF45B7D1), Color(0xFF96CEB4), Color(0xFFFF9F43),
        )
        val count = if (bonus) 34 else 20
        repeat(count) { i ->
            val angle = i * (360.0 / count) * (Math.PI / 180.0)
            val outerRing = i % 2 == 0
            val speedPx = size.minDimension * (if (outerRing) 0.52f else 0.38f) * p
            val gravity = size.minDimension * 0.28f * p * p
            val x = cx + (cos(angle) * speedPx).toFloat()
            val y = cy + (sin(angle) * speedPx + gravity).toFloat()
            val alpha = ((1f - p) * 1.8f).coerceIn(0f, 1f)
            val radius = (size.minDimension * 0.042f * (1f - p * 0.45f)).coerceAtLeast(3f)
            drawCircle(
                color = particleColors[i % particleColors.size].copy(alpha = alpha),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(x, y),
            )
        }
    }
}

@Composable
private fun FirstDoseOverlay(message: String?, bonus: Boolean = false, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1400)
        visible = false
        kotlinx.coroutines.delay(300)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(200)),
                exit = scaleOut(tween(250)) + fadeOut(tween(250)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        message ?: "Anchor set 🎯",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (bonus) "Bonus sparkles. No reason. Enjoy. ✨" else "First dose of the day logged.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseRemindersDialog(
    onPause: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause reminders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Alarms won't fire during the pause. Your dose history is unaffected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                listOf(1 to "1 hour", 2 to "2 hours", 4 to "4 hours", 8 to "8 hours").forEach { (h, label) ->
                    TextButton(
                        onClick = { onPause(h) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─── Quick Tools Row ────────────────────────────────────────────────────────

@Composable
private fun ToolsRow(
    onOpenFocus: () -> Unit,
    onOpenBrainDump: () -> Unit,
    onOpenRoulette: () -> Unit,
    onOpenBreathe: () -> Unit,
    onOpenQuicklist: () -> Unit,
    quicklistCount: Int,
    isPro: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            Text(
                "TOOLS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            if (!isPro) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "PRO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ToolCard(
                emoji = "🧠",
                label = "Focus",
                subtitle = "Timer",
                accentColor = Color(0xFF006A6A),
                onClick = onOpenFocus,
                locked = !isPro,
                modifier = Modifier.weight(1f),
            )
            ToolCard(
                emoji = "💡",
                label = "Brain dump",
                subtitle = "Capture",
                accentColor = Color(0xFF6A1B9A),
                onClick = onOpenBrainDump,
                locked = !isPro,
                modifier = Modifier.weight(1f),
            )
            ToolCard(
                emoji = "🎲",
                label = "Roulette",
                subtitle = "Pick task",
                accentColor = Color(0xFFE65100),
                onClick = onOpenRoulette,
                locked = !isPro,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ToolCard(
                emoji = "🫁",
                label = "Breathe",
                subtitle = "Calm down",
                accentColor = Color(0xFF2E7D32),
                onClick = onOpenBreathe,
                locked = !isPro,
                modifier = Modifier.weight(1f),
            )
            ToolCard(
                emoji = "☑️",
                label = "Quicklist",
                subtitle = if (quicklistCount > 0) "$quicklistCount left" else "Micro-tasks",
                accentColor = Color(0xFF1565C0),
                onClick = onOpenQuicklist,
                locked = !isPro,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToolCard(
    emoji: String,
    label: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    locked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val effectiveAccent = if (locked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                          else accentColor
    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(
                indication = LocalIndication.current,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (locked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                 else accentColor.copy(alpha = 0.08f),
            ),
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(effectiveAccent, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)),
                )
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    Text(
                        text = if (locked) "🔒" else emoji,
                        fontSize = 22.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = effectiveAccent,
                    )
                    Text(
                        if (locked) "Pro" else subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (locked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─── Brain Dump Bottom Sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrainDumpSheet(
    items: List<String>,
    onCapture: (String) -> Unit,
    onPromote: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "💡  Brain dump",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (items.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text(
                            "Clear all",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Text(
                "Capture a fleeting thought before it vanishes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Input area
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("What's on your mind?") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    if (input.isNotBlank()) {
                        onCapture(input.trim())
                        input = ""
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Save")
            }

            // Recent items
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Recent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.reversed().take(10).forEach { thought ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = thought,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                                )
                                // One-tap promote to the Quicklist (Spec #6).
                                TextButton(onClick = { onPromote(thought) }) {
                                    Text(
                                        "→ Quicklist",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Task Roulette Bottom Sheet ─────────────────────────────────────────────

/** Built-in micro-tasks used when the user hasn't captured enough of their own (Spec #5). */
private val STARTER_TASKS = listOf(
    "Drink a glass of water",
    "Stand up and stretch for 1 minute",
    "Reply to one message",
    "Put one thing back where it belongs",
    "2-minute tidy of whatever's in front of you",
    "Step outside for three breaths",
    "Refill your water bottle",
    "Write down the thing you keep re-remembering",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRouletteSheet(
    items: List<String>,
    onRemove: (String) -> Unit,
    onStartTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Blend starter tasks into the wheel until the user has ≥3 of their own thoughts (Spec #5).
    val pool = if (items.size >= 3) items else items + STARTER_TASKS
    var currentIndex by remember(pool.size) {
        mutableIntStateOf(if (pool.isNotEmpty()) pool.indices.random() else -1)
    }
    var respins by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "🎲  Task Roulette",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
            Text(
                "Can't pick? Let the wheel decide.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            )

            val shown = pool.getOrNull(currentIndex)
            if (shown != null) {
                val isStarter = shown !in items
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE65100).copy(alpha = 0.08f),
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(
                                    Color(0xFFE65100),
                                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                                ),
                        )
                        Text(
                            text = shown,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
                if (isStarter) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "starter task",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(20.dp))
                // Start doing it now — a 10-min transition timer removes the "when do I start" gap.
                Button(
                    onClick = onStartTimer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("⏱  Start 10-min timer")
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (respins < 2) {
                        FilledTonalButton(
                            onClick = {
                                currentIndex = pool.indices.filter { it != currentIndex }.randomOrNull() ?: currentIndex
                                respins++
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("🎲  Roll again")
                        }
                    } else {
                        // Respin cap — unlimited rolls just recreate the paralysis the tool breaks.
                        Text(
                            "This one. Trust the wheel.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        onClick = {
                            if (shown in items) onRemove(shown)
                            currentIndex = pool.indices.filter { it != currentIndex }.randomOrNull() ?: -1
                            respins = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("✓  Done")
                    }
                }
            }
        }
    }
}

// ─── Box Breathing Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreatheSheet(onDismiss: () -> Unit) {
    val phases = listOf("Inhale" to 4, "Hold" to 4, "Exhale" to 4, "Hold" to 4)
    var isRunning by remember { mutableStateOf(false) }
    var phaseIndex by remember { mutableIntStateOf(0) }
    var phaseSeconds by remember { mutableIntStateOf(4) }
    var totalCycles by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (isRunning) {
            val (_, duration) = phases[phaseIndex]
            for (remaining in duration downTo 1) {
                phaseSeconds = remaining
                kotlinx.coroutines.delay(1_000L)
                if (!isRunning) return@LaunchedEffect
            }
            val nextPhase = (phaseIndex + 1) % phases.size
            if (nextPhase == 0) totalCycles++
            phaseIndex = nextPhase
            phaseSeconds = phases[nextPhase].second
        }
    }

    val targetScale = when {
        phases[phaseIndex].first == "Inhale" -> 1.35f
        phases[phaseIndex].first == "Exhale" -> 0.65f
        phaseIndex == 1 -> 1.35f  // Hold after Inhale
        else -> 0.65f             // Hold after Exhale
    }
    val circleScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = if (phases[phaseIndex].first == "Hold") 200
                             else (phases[phaseIndex].second * 900),
            easing = if (phases[phaseIndex].first == "Inhale") FastOutSlowInEasing
                     else LinearOutSlowInEasing,
        ),
        label = "breathe",
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "🌬️  Box Breathing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
            Text(
                "4-4-4-4 · Calms the nervous system in 2 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(180.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size((140 * circleScale).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF006A6A).copy(alpha = 0.25f),
                                    Color(0xFF006A6A).copy(alpha = 0.08f),
                                ),
                            ),
                        ),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = phases[phaseIndex].first,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF006A6A),
                    )
                    Text(
                        text = "$phaseSeconds",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF006A6A),
                    )
                }
            }

            Spacer(Modifier.height(if (totalCycles > 0) 4.dp else 24.dp))
            if (totalCycles > 0) {
                Text(
                    text = "$totalCycles cycle${if (totalCycles > 1) "s" else ""} completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            } else {
                Spacer(Modifier.height(20.dp))
            }

            if (isRunning) {
                FilledTonalButton(
                    onClick = { isRunning = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pause")
                }
            } else {
                Button(
                    onClick = {
                        phaseIndex = 0
                        phaseSeconds = 4
                        totalCycles = 0
                        isRunning = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (phaseIndex > 0 || totalCycles > 0) "Restart" else "Start")
                }
            }
        }
    }
}

// ─── Quicklist Bottom Sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuicklistSheet(
    items: List<Pair<String, Boolean>>,
    onAdd: (String) -> Unit,
    onToggle: (String) -> Unit,
    onClearDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val doneCount = items.count { it.second }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "☑️  Quicklist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (doneCount > 0) {
                    TextButton(onClick = onClearDone) {
                        Text(
                            "Clear done ($doneCount)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Micro-tasks. Tap to tick off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Add a task…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
                FilledTonalButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onAdd(input.trim())
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Text("Add")
                }
            }

            Spacer(Modifier.height(16.dp))
            if (items.isEmpty()) {
                Text(
                    "No tasks yet — add one above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { (text, done) ->
                        Card(
                            onClick = { onToggle(text) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (done)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else
                                    Color(0xFF1565C0).copy(alpha = 0.06f),
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = if (done) "✓" else "○",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (done) Color(0xFF1565C0)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (done)
                                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
