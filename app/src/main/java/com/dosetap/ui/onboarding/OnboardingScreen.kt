package com.dosetap.ui.onboarding

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosetap.DoseTapApp
import com.dosetap.domain.model.Medication
import com.dosetap.domain.model.MedicationPreset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dosetap.util.BatteryOptimizationHelper
import com.dosetap.util.ExactAlarmHelper
import com.dosetap.widget.DoseWidgetReceiver
import kotlinx.coroutines.launch

private const val TAG = "OnboardingScreen"

/** Working draft of the medication the user is configuring through onboarding. */
private data class OnboardingDraft(
    val preset: MedicationPreset? = null,
    val name: String = "",
    val followUps: List<Int> = listOf(240, 480),
    val durationMinutes: Int? = null,
    val hasEffectWindow: Boolean = true,
    val dailyReminderMinutes: Int? = null,
    val anchor: String? = null,
) {
    fun toMedication(): Medication = Medication(
        name = name.trim(),
        followUps = followUps,
        durationMinutes = durationMinutes,
        dailyReminderMinutes = dailyReminderMinutes,
        morningAnchor = anchor,
    )

    val isReady: Boolean get() = name.isNotBlank()
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(OnboardingDraft()) }
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container
    val scope = rememberCoroutineScope()

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                    if (!json.isNullOrBlank()) {
                        container.backupManager.importFromJson(json)
                        container.settingsRepository.setOnboardingComplete(true)
                        container.refreshAmbient()
                        onComplete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Restore from backup failed", e)
                }
            }
        }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            slideInHorizontally { it * dir } togetherWith slideOutHorizontally { -it * dir }
        },
        label = "onboarding_step",
    ) { current ->
        when (current) {
            0 -> WelcomeStep(
                onNext = { step = 1 },
                onRestore = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
            )
            1 -> PickMedStep(
                draft = draft,
                onBack = { step = 0 },
                onNext = { updated ->
                    draft = updated
                    step = 2
                },
            )
            2 -> UsualTimeStep(
                draft = draft,
                onBack = { step = 1 },
                onNext = { updated ->
                    draft = updated
                    step = 3
                },
            )
            3 -> AnchorStep(
                draft = draft,
                onBack = { step = 2 },
                onNext = { updated ->
                    draft = updated
                    step = 4
                },
            )
            4 -> PermissionsStep(
                anchor = draft.anchor,
                onNext = { step = 5 },
            )
            5 -> PinWidgetStep(
                onComplete = {
                    scope.launch {
                        try {
                            if (draft.isReady) {
                                container.medicationDao.insert(draft.toMedication())
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save medication during onboarding", e)
                        }
                        container.settingsRepository.setOnboardingComplete(true)
                        container.refreshAmbient()
                        onComplete()
                    }
                },
            )
        }
    }
}

// =========================================================================
// Step 0 — Welcome
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WelcomeStep(onNext: () -> Unit, onRestore: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("💊", fontSize = 72.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text(
                text = "DopaTap",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "One tap. Your data stays on your phone.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    text = "DopaTap is a reminder tool, not a medical device. It does not provide medical advice and is not a substitute for consulting your doctor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(40.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Get started")
            }
            Spacer(Modifier.height(12.dp))
            // Restore from backup — prominent card so returning users don't miss it
            Card(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("📦", fontSize = 24.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Switching from another device?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Tap to restore history from a backup file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
private fun formatFollowupLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

// Step 1 — Pick Med (preset list)
// =========================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickMedStep(
    draft: OnboardingDraft,
    onBack: () -> Unit,
    onNext: (OnboardingDraft) -> Unit,
) {
    var selected by remember { mutableStateOf(draft.preset) }
    var customName by remember { mutableStateOf(if (draft.preset == null) draft.name else "") }
    val customFollowUps = remember { mutableStateListOf(*(draft.followUps.toTypedArray())) }
    val isOther = selected == null && customName.isNotBlank()
    val scope = rememberCoroutineScope()
    val bringCustomFieldIntoView = remember { BringIntoViewRequester() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Which medication?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pick one to pre-fill the timings, or type your own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            MedicationPreset.ALL.forEach { preset ->
                PresetRow(
                    preset = preset,
                    selected = selected == preset,
                    onClick = {
                        selected = preset
                        customName = ""
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Or type something else",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = customName,
                onValueChange = {
                    customName = it
                    if (it.isNotBlank()) selected = null
                },
                placeholder = { Text("e.g. Focalin XR 10mg") },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringCustomFieldIntoView)
                    .onFocusChanged { fs ->
                        if (fs.isFocused) scope.launch { bringCustomFieldIntoView.bringIntoView() }
                    },
                singleLine = true,
            )

            // Follow-up configurator — only shown for custom (non-preset) entries
            if (isOther) {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "Follow-up reminders",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Nudges after you log — how long after?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (customFollowUps.size < 4) {
                        TextButton(onClick = {
                            val last = customFollowUps.lastOrNull() ?: 180
                            customFollowUps.add((last + 60).coerceAtMost(720))
                        }) {
                            Text("+ Add")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (customFollowUps.isEmpty()) {
                    Text(
                        "No follow-ups — just the initial dose log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customFollowUps.forEachIndexed { index, minutes ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Reminder ${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    // − button
                                    TextButton(
                                        onClick = {
                                            val stepped = (minutes - 30).coerceAtLeast(30)
                                            customFollowUps[index] = stepped
                                        },
                                        enabled = minutes > 30,
                                    ) { Text("−", style = MaterialTheme.typography.titleMedium) }

                                    // Time display
                                    Text(
                                        text = "+${formatFollowupLabel(minutes)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )

                                    // + button
                                    TextButton(
                                        onClick = {
                                            val stepped = (minutes + 30).coerceAtMost(720)
                                            customFollowUps[index] = stepped
                                        },
                                        enabled = minutes < 720,
                                    ) { Text("+", style = MaterialTheme.typography.titleMedium) }

                                    // Remove
                                    TextButton(onClick = { customFollowUps.removeAt(index) }) {
                                        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = selected != null || isOther,
                    onClick = {
                        val finalPreset = selected
                        val updated = if (finalPreset != null) {
                            draft.copy(
                                preset = finalPreset,
                                name = finalPreset.label,
                                followUps = finalPreset.followUps,
                                durationMinutes = finalPreset.durationMinutes,
                                hasEffectWindow = finalPreset.hasEffectWindow,
                            )
                        } else {
                            draft.copy(
                                preset = null,
                                name = customName.trim(),
                                followUps = customFollowUps.toList(),
                                durationMinutes = null,
                                hasEffectWindow = true,
                            )
                        }
                        onNext(updated)
                    },
                ) {
                    Text("Continue")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PresetRow(
    preset: MedicationPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preset.durationMinutes?.let { mins ->
                        "≈ ${mins / 60}h effect window"
                    } ?: "Non-stimulant — no acute window",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preset.followUps.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append("${preset.followUps.size} follow-up${if (preset.followUps.size > 1) "s" else ""}: ")
                            append(preset.followUps.joinToString(" · ") { mins ->
                                if (mins % 60 == 0) "+${mins / 60}h" else "+${mins / 60}h ${mins % 60}m"
                            })
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// =========================================================================
// Step 2 — Usual time
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsualTimeStep(
    draft: OnboardingDraft,
    onBack: () -> Unit,
    onNext: (OnboardingDraft) -> Unit,
) {
    val initial = draft.dailyReminderMinutes ?: (8 * 60)
    val timePickerState = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "When do you usually take it?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "We'll send a gentle nudge if you haven't logged by then.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            TimePicker(state = timePickerState)

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val mins = timePickerState.hour * 60 + timePickerState.minute
                        onNext(draft.copy(dailyReminderMinutes = mins))
                    },
                ) {
                    Text("Continue")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// =========================================================================
// Step 3 — Morning Anchor (habit-stacking primitive)
// =========================================================================
private data class AnchorOption(val emoji: String, val label: String, val cue: String)

private val ANCHOR_OPTIONS = listOf(
    AnchorOption("☕", "Coffee", "☕ coffee"),
    AnchorOption("📱", "Phone unlock", "📱 phone unlock"),
    AnchorOption("🪥", "Brushing teeth", "🪥 brushing teeth"),
    AnchorOption("🔔", "Turning alarm off", "🔔 alarm off"),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnchorStep(
    draft: OnboardingDraft,
    onBack: () -> Unit,
    onNext: (OnboardingDraft) -> Unit,
) {
    var selectedCue by remember { mutableStateOf(draft.anchor) }
    var customCue by remember { mutableStateOf("") }
    val presetCues = ANCHOR_OPTIONS.map { it.cue }.toSet()
    val isCustom = selectedCue != null && selectedCue !in presetCues
    val scope = rememberCoroutineScope()
    val bringCustomFieldIntoView = remember { BringIntoViewRequester() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Anchor your dose",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "What do you do every morning without fail? We'll remind you right after that — pairing a new habit with an existing one is the trick that actually works.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            ANCHOR_OPTIONS.forEach { opt ->
                AnchorRow(
                    option = opt,
                    selected = selectedCue == opt.cue,
                    onClick = { selectedCue = opt.cue },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Or something else",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = if (isCustom) selectedCue.orEmpty() else customCue,
                onValueChange = {
                    customCue = it
                    selectedCue = it.ifBlank { null }
                },
                placeholder = { Text("e.g. after I feed the dog") },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringCustomFieldIntoView)
                    .onFocusChanged { fs ->
                        if (fs.isFocused) scope.launch { bringCustomFieldIntoView.bringIntoView() }
                    },
                singleLine = true,
            )

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onNext(draft.copy(anchor = null)) }) { Text("Skip") }
                Button(
                    enabled = !selectedCue.isNullOrBlank(),
                    onClick = { onNext(draft.copy(anchor = selectedCue?.trim())) },
                ) {
                    Text("Continue")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnchorRow(
    option: AnchorOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(option.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(16.dp))
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// =========================================================================
// Step 4 — Permissions (after Anchor, so we can name the cue in the ask)
// =========================================================================
@Composable
private fun PermissionsStep(
    anchor: String?,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    // Both grants are re-checked on resume so the cards flip to their done-state the moment the
    // user returns from the system settings screen.
    var batteryOptimized by remember {
        mutableStateOf(!BatteryOptimizationHelper.isIgnoringBatteryOptimization(context))
    }
    var exactAlarmGranted by remember { mutableStateOf(ExactAlarmHelper.canScheduleExact(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimized = !BatteryOptimizationHelper.isIgnoringBatteryOptimization(context)
                exactAlarmGranted = ExactAlarmHelper.canScheduleExact(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cueLine = if (!anchor.isNullOrBlank()) {
        "We'll remind you right after $anchor."
    } else {
        "We'll remind you at the time you chose."
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Allow reminders",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$cueLine Two quick taps make sure the reminder fires even when your phone is asleep.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (notificationsGranted)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                    ) {
                        if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = if (notificationsGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notifications", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (notificationsGranted) "Allowed ✓" else "Tap to allow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (notificationsGranted) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (!batteryOptimized)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                    ) {
                        if (batteryOptimized) {
                            BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🔋", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery exemption", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (!batteryOptimized) "Done ✓"
                            else "Some phones stop apps in the background. Tap to allow DoseTap to keep its alarms running.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!batteryOptimized) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Exact-alarm permission — only meaningful on Android 12+ (auto-granted below).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (exactAlarmGranted)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                        ) {
                            if (!exactAlarmGranted) ExactAlarmHelper.openExactAlarmSettings(context)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⏰", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exact alarms", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (exactAlarmGranted) "Allowed ✓"
                                else "Let reminders fire at the exact minute, even in Doze. Tap to allow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (exactAlarmGranted) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onNext) {
                Text(
                    "Skip for now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// =========================================================================
// Step 5 — Pin Widget
// =========================================================================
@Composable
private fun PinWidgetStep(onComplete: () -> Unit) {
    val context = LocalContext.current

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Widgets,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Add the widget",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Log your dose in one tap from your home screen — no need to open the app.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val widgetProvider = ComponentName(context, DoseWidgetReceiver::class.java)
                    if (appWidgetManager.isRequestPinAppWidgetSupported) {
                        try {
                            appWidgetManager.requestPinAppWidget(widgetProvider, null, null)
                        } catch (e: Exception) {
                            Log.w(TAG, "requestPinAppWidget failed", e)
                        }
                    }
                    onComplete()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add widget to home screen")
            }
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            ) {
                Text(
                    text = "Long-press your home screen → Widgets → DoseTap → drag to place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            TextButton(onClick = onComplete) {
                Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
