package com.dosetap.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.dosetap.AppContainer
import com.dosetap.DoseTapApp
import com.dosetap.R
import com.dosetap.domain.model.Medication
import com.dosetap.ui.theme.AppTheme
import kotlinx.coroutines.launch

private const val TAG = "WidgetConfigActivity"

@OptIn(ExperimentalMaterial3Api::class)
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Invalid appWidgetId, finishing")
            finish()
            return
        }

        val container = (applicationContext as DoseTapApp).container

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val scope = rememberCoroutineScope()
                    var medications by remember { mutableStateOf<List<Medication>>(emptyList()) }
                    var isLoading by remember { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        medications = container.medicationDao.getAll()
                        isLoading = false

                        if (medications.size == 1) {
                            scope.launch {
                                configureMedication(applicationContext, appWidgetId, medications[0], container)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                finish()
                            }
                        }
                    }

                    when {
                        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                        medications.isEmpty() -> Scaffold { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("💊", style = MaterialTheme.typography.displaySmall)
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.home_no_medication),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.widget_config_no_medication_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.widget_open_app))
                                }
                            }
                        }

                        medications.size > 1 -> Scaffold(
                            topBar = {
                                TopAppBar(title = {
                                    Text(
                                        stringResource(R.string.widget_config_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                })
                            }
                        ) { innerPadding ->
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    Text(
                                        "This widget will show one medication and let you log a dose with one tap.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp),
                                    )
                                }
                                items(medications) { med ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            scope.launch {
                                                configureMedication(applicationContext, appWidgetId, med, container)
                                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                                finish()
                                            }
                                        },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    med.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                if (med.followUps.isNotEmpty()) {
                                                    Text(
                                                        "${med.followUps.size} follow-up${if (med.followUps.size > 1) "s" else ""}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            Icon(
                                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(16.dp)) }
                            }
                        }
                        // Single med: auto-configured in LaunchedEffect, loading spinner shown briefly
                    }
                }
            }
        }
    }

    private suspend fun configureMedication(
        context: Context,
        appWidgetId: Int,
        medication: Medication,
        container: AppContainer,
    ) {
        // Store mapping in SettingsRepository — keyed by raw appWidgetId so provideGlance
        // can always look it up without relying on a GlanceId that may not exist yet
        // (Glance receives ACTION_APPWIDGET_ENABLED only after RESULT_OK, so getGlanceIds()
        // won't include a brand-new widget during its first configuration).
        container.settingsRepository.setWidgetMedication(appWidgetId, medication.id)

        // Refresh all already-registered widget instances so they pick up the new mapping.
        // The newly-configured widget will be triggered automatically by the system after RESULT_OK.
        GlanceAppWidgetManager(context).getGlanceIds(DoseWidget::class.java).forEach { glanceId ->
            DoseWidget().update(context, glanceId)
        }

        Log.d(TAG, "Widget $appWidgetId configured for medication ${medication.name} (id=${medication.id})")
    }
}
