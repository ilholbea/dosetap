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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dosetap.DoseTapApp
import com.dosetap.domain.model.Medication
import com.dosetap.widget.DoseWidgetReceiver
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "OnboardingScreen"
private const val TOTAL_STEPS = 4

private data class MedPreset(
    val label: String,
    val durationMinutes: Int?,
)

private val MED_PRESETS = listOf(
    MedPreset("Ritalin IR", 240),
    MedPreset("Ritalin LA", 480),
    MedPreset("Concerta", 720),
    MedPreset("Adderall IR", 300),
    MedPreset("Adderall XR", 480),
    MedPreset("Vyvanse", 720),
    MedPreset("Strattera", null),
    MedPreset("Other", null),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container
    val scope = rememberCoroutineScope()

    var savedMedication by remember { mutableStateOf<Medication?>(null) }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            slideInHorizontally { it * dir } togetherWith slideOutHorizontally { -it * dir }
        },
        label = "onboarding_step",
    ) { step ->
        when (step) {
            0 -> WelcomeStep(onNext = { currentStep = 1 })
            1 -> PermissionsStep(onNext = { currentStep = 2 })
            2 -> AddMedicationStep(
                onNext = { medication ->
                    savedMedication = medication
                    currentStep = 3
                }
            )
            3 -> PinWidgetStep(
                onComplete = {
                    scope.launch {
                        try {
                            savedMedication?.let { container.medicationDao.insert(it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save medication during onboarding", e)
                        }
                        container.settingsRepository.setOnboardingComplete(true)
                        val med = container.medicationDao.getFirst()
                        if (med != null) {
                            container.ambientNotificationManager.update(med, null)
                        }
                        onComplete()
                    }
                }
            )
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("💊", fontSize = 72.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text(
                text = "DoseTap",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "One tap. Never miss a dose.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    text = "DoseTap is a reminder tool, not a medical device. It does not provide medical advice and is not a substitute for consulting your doctor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(48.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Get started")
            }
        }
    }
}

@Composable
private fun PermissionsStep(onNext: () -> Unit) {
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

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Allow notifications",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "DoseTap shows a quiet status on your lock screen at all times, plus an alarm when it's time to take your medication.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

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

            Spacer(Modifier.height(48.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun AddMedicationStep(onNext: (Medication) -> Unit) {
    var name by remember { mutableStateOf("") }
    var followUp1Hours by remember { mutableFloatStateOf(4f) }
    var followUp2Hours by remember { mutableFloatStateOf(8f) }
    var selectedPreset by remember { mutableStateOf<MedPreset?>(null) }
    var durationMinutes by remember { mutableStateOf<Int?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Add your medication",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Quick-fill or type manually — you can change this anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // Quick-fill preset chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MED_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = preset == selectedPreset,
                        onClick = {
                            selectedPreset = preset
                            if (preset.label != "Other") {
                                name = preset.label
                            }
                            durationMinutes = preset.durationMinutes
                        },
                        label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Medication name") },
                placeholder = { Text("e.g. Ritalin 10mg") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "First reminder: ${followUp1Hours.roundToInt()}h after dose",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = followUp1Hours,
                onValueChange = { followUp1Hours = it },
                valueRange = 1f..8f,
                steps = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Second reminder: ${followUp2Hours.roundToInt()}h after dose",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = followUp2Hours,
                onValueChange = { followUp2Hours = it },
                valueRange = 2f..12f,
                steps = 9,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) return@Button
                    onNext(
                        Medication(
                            name = trimmed,
                            followUps = listOf(
                                followUp1Hours.roundToInt() * 60,
                                followUp2Hours.roundToInt() * 60,
                            ),
                            durationMinutes = durationMinutes,
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

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
