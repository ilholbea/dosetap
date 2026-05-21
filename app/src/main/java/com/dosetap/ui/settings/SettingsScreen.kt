package com.dosetap.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetap.BuildConfig
import com.dosetap.DoseTapApp
import com.dosetap.R
import com.dosetap.domain.model.Medication
import com.dosetap.util.BatteryOptimizationHelper
import com.dosetap.util.Constants
import com.dosetap.util.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container

    val viewModel: SettingsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(container, context) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importFromUri(uri)
    }

    // Share export file when ready
    LaunchedEffect(uiState.exportFileUri) {
        val uri = uiState.exportFileUri ?: return@LaunchedEffect
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "DoseTap Backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share DoseTap Backup"))
        viewModel.clearExportUri()
    }

    // Import result snackbar
    LaunchedEffect(uiState.importResult, uiState.importError) {
        uiState.importResult?.let {
            snackbarHostState.showSnackbar("Imported ${it.medicationsCount} medication(s) and ${it.eventsCount} dose event(s)")
            viewModel.clearImportResult()
        }
        uiState.importError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportResult()
        }
    }

    // Delete confirm dialog
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            title = { Text(stringResource(R.string.settings_delete_data_confirm)) },
            text = { Text(stringResource(R.string.settings_delete_data_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAllData() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    )
                ) {
                    Text(stringResource(R.string.settings_delete_data_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirm() }) {
                    Text(stringResource(R.string.settings_delete_data_confirm_no))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.cd_back_button)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Medication section
            item {
                SectionHeader(stringResource(R.string.settings_medication_section))
                MedicationSection(
                    medication = uiState.medication,
                    onSave = { viewModel.saveMedication(it) },
                )
            }

            // Reliability section
            item {
                SectionHeader(stringResource(R.string.settings_reliability_section))
                ReliabilitySection(
                    isBatteryOptimized = uiState.isBatteryOptimized,
                    isKnownBadOem = uiState.isKnownBadOem,
                    oemName = uiState.oemName,
                    testReminderScheduled = uiState.testReminderScheduled,
                    onTestReminder = { viewModel.scheduleTestReminder() },
                    onFixBattery = { BatteryOptimizationHelper.openBatteryOptimizationSettings(context) },
                    onFixOem = { BatteryOptimizationHelper.openOemSettings(context) },
                )
            }

            // Data section
            item {
                SectionHeader(stringResource(R.string.settings_data_section))
                DataSection(
                    onExport = { viewModel.exportData() },
                    onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    onDelete = { viewModel.showDeleteConfirm() },
                )
            }

            // About section
            item {
                SectionHeader(stringResource(R.string.settings_about_section))
                AboutSection()
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
    )
}

@Composable
private fun MedicationSection(
    medication: Medication?,
    onSave: (Medication) -> Unit,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var name by remember(medication) { mutableStateOf(medication?.name ?: "") }
    val followUps = remember(medication) {
        mutableStateListOf(*(medication?.followUps ?: Constants.DEFAULT_FOLLOWUPS).toTypedArray())
    }
    var soundUri by remember(medication) { mutableStateOf(medication?.soundUri) }
    var dailyReminderMinutes by remember(medication) { mutableStateOf(medication?.dailyReminderMinutes) }
    var lateCutoffMinutes by remember(medication) { mutableStateOf(medication?.lateCutoffMinutes) }
    var pillCount by remember(medication) { mutableStateOf(medication?.pillCount) }
    var refillAlertAt by remember(medication) { mutableIntStateOf(medication?.refillAlertAt ?: 7) }
    val offDays = remember(medication) {
        mutableStateListOf(*(medication?.offDays ?: emptyList<Int>()).toTypedArray())
    }
    var durationMinutes by remember(medication) { mutableStateOf(medication?.durationMinutes) }
    var durationText by remember(medication) { mutableStateOf(medication?.durationMinutes?.toString() ?: "") }
    var pillCountText by remember(medication) { mutableStateOf(medication?.pillCount?.toString() ?: "") }
    var showDailyTimePicker by remember { mutableStateOf(false) }
    var showCutoffTimePicker by remember { mutableStateOf(false) }
    var showSetPillCountDialog by remember { mutableStateOf(false) }

    // Alarm tone picker
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            soundUri = uri?.toString()
        }
    }

    fun soundDisplayName(): String {
        if (soundUri == null) return "Default alarm"
        return try {
            RingtoneManager.getRingtone(context, Uri.parse(soundUri))?.getTitle(context) ?: "Custom"
        } catch (e: Exception) { "Custom" }
    }

    fun minutesToDisplay(minutes: Int?): String {
        if (minutes == null) return "Off"
        val h = minutes / 60
        val m = minutes % 60
        val amPm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return "%d:%02d %s".format(h12, m, amPm)
    }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!editing) {
                if (medication != null) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_followups_summary,
                            medication.followUps.joinToString(", ") { TimeFormatter.formatInterval(it) }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (medication.pillCount != null) {
                        Text(
                            text = "${medication.pillCount} pills remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (medication.pillCount <= medication.refillAlertAt)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (medication.durationMinutes != null) {
                        Text(
                            text = "Effect window: ${medication.durationMinutes / 60}h ${medication.durationMinutes % 60}min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { editing = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Text(stringResource(R.string.settings_medication_edit), modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.home_no_medication),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { editing = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.home_add_medication))
                    }
                }
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.onboarding_medication_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.onboarding_medication_followups_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                followUps.forEachIndexed { index, minutes ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.onboarding_medication_followup_label,
                                index + 1,
                                TimeFormatter.formatInterval(minutes),
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { followUps.removeAt(index) }) {
                            Text(stringResource(R.string.onboarding_medication_remove_followup))
                        }
                    }
                    val steps = (Constants.MAX_FOLLOWUP_MINUTES - Constants.MIN_FOLLOWUP_MINUTES) / Constants.FOLLOWUP_STEP_MINUTES - 1
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { followUps[index] = (it / Constants.FOLLOWUP_STEP_MINUTES).toInt() * Constants.FOLLOWUP_STEP_MINUTES },
                        valueRange = Constants.MIN_FOLLOWUP_MINUTES.toFloat()..Constants.MAX_FOLLOWUP_MINUTES.toFloat(),
                        steps = steps,
                    )
                }

                if (followUps.size < Constants.MAX_FOLLOWUP_COUNT) {
                    TextButton(onClick = { followUps.add(60) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.onboarding_medication_add_followup))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Alarm tone row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Alarm tone", style = MaterialTheme.typography.bodySmall)
                        Text(soundDisplayName(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        ringtoneLauncher.launch(intent)
                    }) { Text("Change") }
                }

                // Daily reminder row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Daily reminder", style = MaterialTheme.typography.bodySmall)
                        Text(minutesToDisplay(dailyReminderMinutes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { showDailyTimePicker = true }) {
                        Text(if (dailyReminderMinutes == null) "Set" else "Edit")
                    }
                    if (dailyReminderMinutes != null) {
                        TextButton(onClick = { dailyReminderMinutes = null }) { Text("Off") }
                    }
                }

                // Late cutoff row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("\"Too late\" cutoff", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (lateCutoffMinutes == null) "Off" else "After ${minutesToDisplay(lateCutoffMinutes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showCutoffTimePicker = true }) {
                        Text(if (lateCutoffMinutes == null) "Set" else "Edit")
                    }
                    if (lateCutoffMinutes != null) {
                        TextButton(onClick = { lateCutoffMinutes = null }) { Text("Off") }
                    }
                }

                // Off-days row
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        "Off-days (no reminder)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val dayLabels = listOf(
                    java.util.Calendar.SUNDAY to "Sun",
                    java.util.Calendar.MONDAY to "Mon",
                    java.util.Calendar.TUESDAY to "Tue",
                    java.util.Calendar.WEDNESDAY to "Wed",
                    java.util.Calendar.THURSDAY to "Thu",
                    java.util.Calendar.FRIDAY to "Fri",
                    java.util.Calendar.SATURDAY to "Sat",
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    dayLabels.forEach { (dow, label) ->
                        FilterChip(
                            selected = dow in offDays,
                            onClick = {
                                if (dow in offDays) offDays.remove(dow) else offDays.add(dow)
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                // Duration row
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Effect window (hours)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (durationMinutes == null) "Not set" else "${durationMinutes!! / 60}h ${durationMinutes!! % 60}min",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { v ->
                        durationText = v.filter { it.isDigit() }.take(4)
                        durationMinutes = if (durationText.isEmpty()) null else durationText.toIntOrNull()
                    },
                    label = { Text("Duration (minutes)", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    supportingText = { Text("e.g. 240 for 4-hour IR tablet") },
                )

                // Pill count row
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalPharmacy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Pill count tracking", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (pillCount == null) "Disabled" else "$pillCount pills remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showSetPillCountDialog = true }) {
                        Text(if (pillCount == null) "Enable" else "Update")
                    }
                    if (pillCount != null) {
                        TextButton(onClick = { pillCount = null; pillCountText = "" }) { Text("Off") }
                    }
                }

                if (pillCount != null) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = refillAlertAt.toString(),
                        onValueChange = { v ->
                            v.filter { it.isDigit() }.toIntOrNull()?.let { refillAlertAt = it }
                        },
                        label = { Text("Refill warning at (pills)", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                    )
                }

                if (showSetPillCountDialog) {
                    var newCountText by remember { mutableStateOf(pillCount?.toString() ?: "") }
                    AlertDialog(
                        onDismissRequest = { showSetPillCountDialog = false },
                        title = { Text("Set pill count") },
                        text = {
                            OutlinedTextField(
                                value = newCountText,
                                onValueChange = { newCountText = it.filter { c -> c.isDigit() }.take(4) },
                                label = { Text("Pills remaining") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                ),
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                pillCount = newCountText.toIntOrNull()
                                pillCountText = newCountText
                                showSetPillCountDialog = false
                            }) { Text("Set") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSetPillCountDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { editing = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_delete_data_confirm_no))
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    (medication ?: Medication(name = "", followUps = emptyList())).copy(
                                        name = name.trim(),
                                        followUps = followUps.toList(),
                                        soundUri = soundUri,
                                        dailyReminderMinutes = dailyReminderMinutes,
                                        lateCutoffMinutes = lateCutoffMinutes,
                                        pillCount = pillCount,
                                        refillAlertAt = refillAlertAt,
                                        offDays = offDays.toList(),
                                        durationMinutes = durationMinutes,
                                    )
                                )
                                editing = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_save))
                    }
                }

                // Daily reminder time picker dialog
                if (showDailyTimePicker) {
                    val initMinutes = dailyReminderMinutes ?: 9 * 60
                    TimePickerDialog(
                        initialHour = initMinutes / 60,
                        initialMinute = initMinutes % 60,
                        onConfirm = { h, m -> dailyReminderMinutes = h * 60 + m; showDailyTimePicker = false },
                        onDismiss = { showDailyTimePicker = false },
                    )
                }

                // Cutoff time picker dialog
                if (showCutoffTimePicker) {
                    val initMinutes = lateCutoffMinutes ?: 16 * 60
                    TimePickerDialog(
                        initialHour = initMinutes / 60,
                        initialMinute = initMinutes % 60,
                        onConfirm = { h, m -> lateCutoffMinutes = h * 60 + m; showCutoffTimePicker = false },
                        onDismiss = { showCutoffTimePicker = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReliabilitySection(
    isBatteryOptimized: Boolean,
    isKnownBadOem: Boolean,
    oemName: String,
    testReminderScheduled: Boolean,
    onTestReminder: () -> Unit,
    onFixBattery: () -> Unit,
    onFixOem: () -> Unit,
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Battery optimization card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isBatteryOptimized)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.primaryContainer,
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isBatteryOptimized) Icons.Filled.Battery0Bar else Icons.Filled.BatteryFull,
                    contentDescription = null,
                    tint = if (isBatteryOptimized) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.reliability_battery_opt_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (isBatteryOptimized)
                            stringResource(R.string.reliability_battery_opt_desc)
                        else
                            stringResource(R.string.reliability_battery_opt_ok),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (isBatteryOptimized) {
                    TextButton(onClick = onFixBattery) {
                        Text(stringResource(R.string.reliability_battery_opt_fix))
                    }
                }
            }
        }

        // OEM warning
        if (isKnownBadOem) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Text(
                            text = stringResource(R.string.reliability_oem_warning_title, oemName),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.reliability_oem_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onFixOem,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val cdFix = stringResource(R.string.cd_reliability_fix, oemName)
                        Text(
                            text = stringResource(R.string.reliability_fix_button),
                            modifier = Modifier.semantics { contentDescription = cdFix }
                        )
                    }
                }
            }
        }

        // Test reminder
        FilledTonalButton(
            onClick = onTestReminder,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null)
            Text(
                text = if (testReminderScheduled)
                    stringResource(R.string.settings_test_reminder_scheduled)
                else
                    stringResource(R.string.settings_test_reminder),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun DataSection(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text("Export backup (JSON)", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Upload, contentDescription = null)
            Text("Import backup", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text(stringResource(R.string.settings_delete_data), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_about_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.settings_privacy_url)))
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.settings_privacy_policy))
                }
                Text(
                    text = stringResource(
                        R.string.settings_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                )
            }
        }
    }
}
