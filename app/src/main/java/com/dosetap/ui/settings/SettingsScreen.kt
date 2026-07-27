package com.dosetap.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import com.dosetap.ui.theme.IconChip
import com.dosetap.ui.theme.accents
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Battery0Bar
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.dosetap.BuildConfig
import com.dosetap.DoseTapApp
import com.dosetap.R
import com.dosetap.domain.model.FeatureFlag
import com.dosetap.domain.model.Medication
import com.dosetap.ui.paywall.PaywallBottomSheet
import com.dosetap.util.BatteryOptimizationHelper
import com.dosetap.util.Constants
import com.dosetap.util.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReport: () -> Unit = {},
) {
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
    val isPro by container.isPro.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPaywall by remember { mutableStateOf(false) }
    var showAddMedication by remember { mutableStateOf(false) }

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
            putExtra(Intent.EXTRA_SUBJECT, "DopaTap Backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share DopaTap Backup"))
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

    if (showPaywall) {
        PaywallBottomSheet(
            price = container.billingManager.formattedPrice(),
            onPurchase = { activity -> container.billingManager.launchPurchase(activity) },
            onRestore = { container.billingManager.restorePurchases() },
            onGiftCode = { code ->
                when (val result = container.giftCodeManager.validate(code)) {
                    com.dosetap.billing.GiftCodeManager.CodeResult.DevGrant -> { viewModel.redeemDevGrant(); result }
                    com.dosetap.billing.GiftCodeManager.CodeResult.PermanentGift -> { viewModel.redeemPermanentGift(); result }
                    com.dosetap.billing.GiftCodeManager.CodeResult.SponsorGift -> { viewModel.unlockProViaGiftCode(); result }
                    com.dosetap.billing.GiftCodeManager.CodeResult.Invalid -> null
                }
            },
            onDismiss = { showPaywall = false },
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
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
            // Medication section — one card per medication
            item {
                SectionHeader(
                    stringResource(R.string.settings_medication_section),
                    subtitle = "Name, reminder times, and optional safety limits.",
                )
            }
            items(uiState.medications, key = { it.id }) { med ->
                MedicationSection(
                    medication = med,
                    isPro = isPro,
                    canDelete = uiState.medications.size > 1,
                    onSave = { viewModel.saveMedication(it) },
                    onDelete = { viewModel.deleteMedication(med.id) },
                    onShowPaywall = { showPaywall = true },
                )
            }
            if (uiState.medications.isEmpty() || showAddMedication) {
                item {
                    MedicationSection(
                        medication = null,
                        isPro = isPro,
                        canDelete = false,
                        onSave = { viewModel.saveMedication(it); showAddMedication = false },
                        onDelete = {},
                        onShowPaywall = { showPaywall = true },
                    )
                }
            }
            if (uiState.medications.isNotEmpty() && !showAddMedication) {
                item {
                    val canAdd = isPro
                    OutlinedButton(
                        onClick = if (canAdd) {
                            { showAddMedication = true }
                        } else {
                            { showPaywall = true }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("Add medication", modifier = Modifier.padding(start = 8.dp))
                        if (!canAdd) {
                            Spacer(Modifier.weight(1f))
                            ProBadge(onClick = null)
                        }
                    }
                }
            }

            // Reliability section
            item {
                SectionHeader(
                    stringResource(R.string.settings_reliability_section),
                    subtitle = "Android sometimes kills background apps. These checks keep your reminders firing.",
                )
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
                SectionHeader(
                    stringResource(R.string.settings_data_section),
                    subtitle = "Back up everything to a JSON file you control.",
                )
                DataSection(
                    isPro = isPro,
                    onExport = { viewModel.exportData() },
                    onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    onDelete = { viewModel.showDeleteConfirm() },
                    onShowPaywall = { showPaywall = true },
                )
            }

            // Sponsor-a-Friend section (Pro)
            if (isPro) {
                item {
                    SectionHeader(
                        "Sponsor a Friend",
                        subtitle = "Give someone a 2-week trial — they get full Pro features free.",
                    )
                    SponsorSection(container = container)
                }
            }

            // Doctor Report (Pro, only when there's enough data to be useful)
            if (isPro && uiState.medications.isNotEmpty()) {
                item {
                    SectionHeader("Reports")
                    OutlinedButton(
                        onClick = onNavigateToReport,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                        Text("Doctor Visit Report (PDF)", modifier = Modifier.padding(start = 8.dp))
                    }
                }
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
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MedicationSection(
    medication: Medication?,
    isPro: Boolean,
    canDelete: Boolean,
    onSave: (Medication) -> Unit,
    onDelete: () -> Unit,
    onShowPaywall: () -> Unit,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(medication == null) }
    var name by remember(medication) { mutableStateOf(medication?.name ?: "") }
    val followUps = remember(medication, isPro) {
        val stored = medication?.followUps ?: Constants.DEFAULT_FOLLOWUPS
        mutableStateListOf(*stored.take(FeatureFlag.maxFollowUps(isPro)).toTypedArray())
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
    var prescriptionDate by remember(medication) { mutableLongStateOf(medication?.prescriptionDate ?: 0L) }
    var daysSupply by remember(medication) { mutableStateOf(medication?.daysSupply) }
    var daysSupplyText by remember(medication) { mutableStateOf(medication?.daysSupply?.toString() ?: "") }
    var refillBufferDays by remember(medication) { mutableIntStateOf(medication?.refillBufferDays ?: 7) }
    var showDailyTimePicker by remember { mutableStateOf(false) }
    var showCutoffTimePicker by remember { mutableStateOf(false) }
    var showSetPillCountDialog by remember { mutableStateOf(false) }
    var showPrescriptionDatePicker by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { editing = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.settings_medication_edit), modifier = Modifier.padding(start = 6.dp))
                        }
                        if (canDelete) {
                            OutlinedButton(
                                onClick = onDelete,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            }
                        }
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

                if (followUps.size < FeatureFlag.maxFollowUps(isPro)) {
                    TextButton(onClick = { followUps.add(60) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text(stringResource(R.string.onboarding_medication_add_followup))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Late cutoff row (free — it's a safety feature)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconChip(Icons.Rounded.AccessTime, accents().amber, size = 34.dp, iconSize = 18.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Late-dose warning", style = MaterialTheme.typography.bodySmall)
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

                // Alarm tone row (Pro)
                ProFeatureRow(
                    icon = { IconChip(Icons.Rounded.MusicNote, accents().green, size = 34.dp, iconSize = 18.dp) },
                    label = "Alarm tone",
                    sublabel = soundDisplayName(),
                    isPro = isPro,
                    onShowPaywall = onShowPaywall,
                ) {
                    TextButton(onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        ringtoneLauncher.launch(intent)
                    }) { Text("Change") }
                }

                // Daily reminder row (Pro)
                ProFeatureRow(
                    icon = { IconChip(Icons.Rounded.Notifications, accents().coral, size = 34.dp, iconSize = 18.dp) },
                    label = "Daily reminder",
                    sublabel = minutesToDisplay(dailyReminderMinutes),
                    isPro = isPro,
                    onShowPaywall = onShowPaywall,
                ) {
                    TextButton(onClick = { showDailyTimePicker = true }) {
                        Text(if (dailyReminderMinutes == null) "Set" else "Edit")
                    }
                    if (dailyReminderMinutes != null) {
                        TextButton(onClick = { dailyReminderMinutes = null }) { Text("Off") }
                    }
                }

                // Advanced (optional) — collapsible section for power-user settings
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = androidx.compose.foundation.LocalIndication.current,
                        ) { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Advanced (optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Effect window, off-days, pill tracking, refill prediction",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (showAdvanced) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (showAdvanced) {
                    Spacer(Modifier.height(8.dp))

                    // Off-days row (Pro)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconChip(Icons.Rounded.CalendarMonth, accents().violet, size = 34.dp, iconSize = 18.dp)
                        Text(
                            "Off-days (no reminder)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        if (!isPro) ProBadge(onClick = onShowPaywall)
                    }
                    if (isPro) {
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
                    }

                    // Effect window (Pro)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ProFeatureRow(
                        icon = { IconChip(Icons.Rounded.Schedule, accents().blue, size = 34.dp, iconSize = 18.dp) },
                        label = "Effect window",
                        sublabel = if (durationMinutes == null) "Not set"
                        else "${durationMinutes!! / 60}h ${durationMinutes!! % 60}min",
                        isPro = isPro,
                        onShowPaywall = onShowPaywall,
                    ) {
                        TextButton(onClick = { showDurationPicker = true }) {
                            Text(if (durationMinutes == null) "Set" else "Edit")
                        }
                        if (durationMinutes != null) {
                            TextButton(onClick = { durationMinutes = null; durationText = "" }) { Text("Off") }
                        }
                    }

                    // Pill count (Pro)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ProFeatureRow(
                        icon = { IconChip(Icons.Rounded.LocalPharmacy, accents().teal, size = 34.dp, iconSize = 18.dp) },
                        label = "Pill count tracking",
                        sublabel = if (pillCount == null) "Disabled" else "$pillCount pills remaining",
                        isPro = isPro,
                        onShowPaywall = onShowPaywall,
                    ) {
                        TextButton(onClick = { showSetPillCountDialog = true }) {
                            Text(if (pillCount == null) "Enable" else "Update")
                        }
                        if (pillCount != null) {
                            TextButton(onClick = { pillCount = null; pillCountText = "" }) { Text("Off") }
                        }
                    }
                    if (isPro && pillCount != null) {
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

                    // Prescription refill prediction (Pro)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ProFeatureRow(
                        icon = { IconChip(Icons.Rounded.CalendarMonth, accents().pink, size = 34.dp, iconSize = 18.dp) },
                        label = "Prescription refill prediction",
                        sublabel = if (prescriptionDate > 0L && daysSupply != null) {
                            val runOutMillis = prescriptionDate + daysSupply!! * 24L * 60 * 60 * 1000
                            "Runs out ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(runOutMillis))}"
                        } else "Not configured",
                        isPro = isPro,
                        onShowPaywall = onShowPaywall,
                    ) {
                        TextButton(onClick = { showPrescriptionDatePicker = true }) { Text("Set date") }
                    }
                    if (isPro) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = daysSupplyText,
                            onValueChange = { v ->
                                daysSupplyText = v.filter { it.isDigit() }.take(3)
                                daysSupply = daysSupplyText.toIntOrNull()
                            },
                            label = { Text("Days supply", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            ),
                            supportingText = { Text("e.g. 30 for a monthly prescription") },
                        )
                        if (prescriptionDate > 0L && daysSupply != null) {
                            val runOutMillis = prescriptionDate + daysSupply!! * 24L * 60 * 60 * 1000
                            val daysLeft = ((runOutMillis - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt()
                            val fmt = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (daysLeft <= refillBufferDays)
                                        MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.secondaryContainer,
                                ),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            ) {
                                Text(
                                    text = if (daysLeft <= 0) "Prescription has run out"
                                    else "~$daysLeft days remaining — runs out ${fmt.format(java.util.Date(runOutMillis))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }

                // Dialogs for advanced fields
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

                if (showDurationPicker) {
                    var hoursText by remember { mutableStateOf(((durationMinutes ?: 0) / 60).toString()) }
                    var minsText by remember { mutableStateOf(((durationMinutes ?: 0) % 60).toString()) }
                    AlertDialog(
                        onDismissRequest = { showDurationPicker = false },
                        title = { Text("Effect window") },
                        text = {
                            Column {
                                Text(
                                    "How long does this medication stay active?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = hoursText,
                                        onValueChange = { hoursText = it.filter { c -> c.isDigit() }.take(2) },
                                        label = { Text("Hours") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                        ),
                                    )
                                    OutlinedTextField(
                                        value = minsText,
                                        onValueChange = { minsText = it.filter { c -> c.isDigit() }.take(2) },
                                        label = { Text("Minutes") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                        ),
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val h = hoursText.toIntOrNull() ?: 0
                                val m = minsText.toIntOrNull() ?: 0
                                val total = h * 60 + m
                                durationMinutes = if (total > 0) total else null
                                durationText = total.toString()
                                showDurationPicker = false
                            }) { Text("Set") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDurationPicker = false }) { Text("Cancel") }
                        }
                    )
                }

                if (showPrescriptionDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = if (prescriptionDate > 0L) prescriptionDate
                        else System.currentTimeMillis(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showPrescriptionDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { prescriptionDate = it }
                                showPrescriptionDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPrescriptionDatePicker = false }) { Text("Cancel") }
                        },
                    ) {
                        DatePicker(state = datePickerState)
                    }
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
                                        prescriptionDate = if (prescriptionDate > 0L) prescriptionDate else null,
                                        daysSupply = daysSupply,
                                        refillBufferDays = refillBufferDays,
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
                    imageVector = if (isBatteryOptimized) Icons.Rounded.Battery0Bar else Icons.Rounded.BatteryFull,
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
                        Icon(Icons.Rounded.Warning, contentDescription = null)
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
            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = accents().amber)
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
    isPro: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onShowPaywall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = if (isPro) onExport else onShowPaywall,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null, tint = accents().blue)
            Text("Export backup (JSON)", modifier = Modifier.padding(start = 8.dp))
            if (!isPro) {
                Spacer(Modifier.weight(1f))
                ProBadge(onClick = null)
            }
        }
        OutlinedButton(
            onClick = if (isPro) onImport else onShowPaywall,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Upload, contentDescription = null, tint = accents().teal)
            Text("Import backup", modifier = Modifier.padding(start = 8.dp))
            if (!isPro) {
                Spacer(Modifier.weight(1f))
                ProBadge(onClick = null)
            }
        }
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = null)
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
private fun ProBadge(onClick: (() -> Unit)?) {
    val modifier = if (onClick != null)
        Modifier.clickable(onClick = onClick)
    else
        Modifier
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(Color(0xFFF57F17).copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = Color(0xFFF57F17),
        )
        Text("Pro", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57F17), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProFeatureRow(
    icon: @Composable () -> Unit,
    label: String,
    sublabel: String,
    isPro: Boolean,
    onShowPaywall: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(sublabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isPro) {
            actions()
        } else {
            ProBadge(onClick = onShowPaywall)
        }
    }
}

@Composable
private fun SponsorSection(container: com.dosetap.AppContainer) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val slotsUsed by container.settingsRepository.sponsorSlotsUsed.collectAsStateWithLifecycle(0)
    val slotAvailable = slotsUsed < com.dosetap.billing.SponsorCodeManager.MAX_SLOTS
    val sponsorCode = remember { container.sponsorCodeManager.generateCode(slotsUsed) }
    var copied by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = accents().pink)
                Text(
                    "Give a 2-week trial to a friend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            if (slotAvailable) {
                Text(
                    "Share this code — your friend gets full Pro features free for 14 days. After that they can keep the free plan or unlock Pro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            sponsorCode,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("DopaTap sponsor code", sponsorCode))
                        copied = true
                        scope.launch {
                            kotlinx.coroutines.delay(2000)
                            copied = false
                        }
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(if (copied) "Copied!" else "Copy", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "You get 1 gift code per Pro device. Their 14 days start when they redeem it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "You've already shared your gift code. Thank you for spreading the word!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
