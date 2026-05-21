package com.dosetap.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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

    // Share export data when available
    LaunchedEffect(uiState.exportData) {
        if (uiState.exportData != null) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, uiState.exportData)
                putExtra(Intent.EXTRA_SUBJECT, "DoseTap Data Export")
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share DoseTap Data"))
            viewModel.clearExportData()
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
    var editing by remember { mutableStateOf(false) }
    var name by remember(medication) { mutableStateOf(medication?.name ?: "") }
    val followUps = remember(medication) {
        mutableStateListOf(*(medication?.followUps ?: Constants.DEFAULT_FOLLOWUPS).toTypedArray())
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
                    TextButton(
                        onClick = { followUps.add(60) },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.onboarding_medication_add_followup))
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
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text(stringResource(R.string.settings_export_data), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text(stringResource(R.string.settings_delete_data), modifier = Modifier.padding(start = 8.dp))
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
