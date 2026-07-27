package com.dosetap.ui.report

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dosetap.DoseTapApp
import com.dosetap.domain.model.DoseAction
import com.dosetap.domain.model.DoseEvent
import com.dosetap.domain.model.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class ReportData(
    val medication: Medication?,
    val events: List<DoseEvent>,
    val adherence30Days: Float,
    val adherenceWeekdays: Float,
    val adherenceWeekends: Float,
    val streak: Int,
    val totalDoses: Int,
    /** Median first-dose-of-day time in "HH:mm" form, null if no dosed days. */
    val firstDoseMedian: String?,
    /** Earliest first-dose time in "HH:mm" form. */
    val firstDoseEarliest: String?,
    /** Latest first-dose time in "HH:mm" form. */
    val firstDoseLatest: String?,
    /** Day-start UTC millis for days in the last 30 with no logged dose. */
    val missedDays: List<Long>,
    /** Tag string → count, top 5. */
    val topTags: List<Pair<String, Int>>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorReportScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container
    var reportData by remember { mutableStateOf<ReportData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val data = withContext(Dispatchers.IO) { loadReport(container) }
        reportData = data
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Doctor Report",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isLoading && reportData != null) {
                        IconButton(onClick = {
                            reportData?.let { printReport(context, it) }
                        }) {
                            Icon(Icons.Rounded.Print, contentDescription = "Print / Save PDF")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val data = reportData
        if (data == null || data.medication == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Not enough data yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pick a longer range, or come back after a few weeks of logging. The report works best with 30+ days of data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Patient Medication Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Generated ${SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Medication", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(data.medication.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (data.medication.durationMinutes != null) {
                            Text(
                                "Effect window: ${data.medication.durationMinutes / 60}h ${data.medication.durationMinutes % 60}min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Adherence", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        ReportRow("30-day overall", "${(data.adherence30Days * 100).toInt()}%")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        ReportRow("Weekdays", "${(data.adherenceWeekdays * 100).toInt()}%")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        ReportRow("Weekends", "${(data.adherenceWeekends * 100).toInt()}%")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        ReportRow("Total doses logged", "${data.totalDoses}")
                    }
                }
            }

            if (data.firstDoseMedian != null) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Time of first dose", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            val rangeText = if (data.firstDoseEarliest != null && data.firstDoseLatest != null) {
                                "usually ${data.firstDoseMedian}, range ${data.firstDoseEarliest}–${data.firstDoseLatest}"
                            } else data.firstDoseMedian
                            Text(rangeText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (data.missedDays.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Days without a logged dose (last 30)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${data.missedDays.size} day(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
                            Text(
                                data.missedDays.joinToString(", ") { fmt.format(Date(it)) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (data.topTags.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Most common notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            data.topTags.forEach { (tag, count) ->
                                ReportRow(tag, "${count}x")
                            }
                        }
                    }
                }
            }

            // Last 30 days log
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Last 30 Days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        val fmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                        val confirmedActions = setOf(DoseAction.INITIAL_TAP, DoseAction.TAKEN)
                        data.events
                            .filter { it.action in confirmedActions && (it.actionedAt) >= cutoff }
                            .sortedByDescending { it.actionedAt }
                            .forEach { event ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        fmt.format(Date(event.actionedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        timeFmt.format(Date(event.actionedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                    }
                }
            }

            item {
                Button(
                    onClick = { reportData?.let { printReport(context, it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Print, contentDescription = null)
                    Text("Save as PDF / Print", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private suspend fun loadReport(container: com.dosetap.AppContainer): ReportData {
    val medication = container.medicationDao.getFirst()
    val allEvents = container.doseEventDao.getAll()
    val confirmedActions = setOf(DoseAction.INITIAL_TAP, DoseAction.TAKEN)

    val cutoff30 = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val oneDayMillis = 24L * 60 * 60 * 1000

    fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val eventsByDay = allEvents.groupBy { startOfDay(it.actionedAt) }
    val todayStart = startOfDay(System.currentTimeMillis())

    val taken30 = (0 until 30).count { daysAgo ->
        val dayStart = startOfDay(System.currentTimeMillis() - daysAgo * oneDayMillis)
        eventsByDay[dayStart]?.any { it.action in confirmedActions } == true
    }

    var streak = 0
    var i = 0
    while (true) {
        val dayStart = startOfDay(System.currentTimeMillis() - i * oneDayMillis)
        if (eventsByDay[dayStart]?.any { it.action in confirmedActions } == true) {
            streak++; i++
        } else break
    }

    val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val recent = allEvents.filter { it.actionedAt >= cutoff && it.action in confirmedActions }

    // Weekday vs weekend adherence split.
    var wkdDosed = 0; var wkdTotal = 0; var wkeDosed = 0; var wkeTotal = 0
    for (daysAgo in 0 until 30) {
        val dayStart = startOfDay(System.currentTimeMillis() - daysAgo * oneDayMillis)
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val dosed = eventsByDay[dayStart]?.any { it.action in confirmedActions } == true
        if (isWeekend) { wkeTotal++; if (dosed) wkeDosed++ }
        else { wkdTotal++; if (dosed) wkdDosed++ }
    }

    // First-dose-of-day time distribution.
    val firstDosesByDay = recent
        .groupBy { startOfDay(it.actionedAt) }
        .mapValues { (_, dayEvents) -> dayEvents.minByOrNull { it.actionedAt }!!.actionedAt }
        .values
        .sorted()
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    fun timeOfDayMins(ts: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
    val firstMedian = firstDosesByDay
        .map { timeOfDayMins(it) }
        .sorted()
        .let { if (it.isEmpty()) null else it[it.size / 2] }
        ?.let { String.format(Locale.getDefault(), "%02d:%02d", it / 60, it % 60) }
    val firstEarliest = firstDosesByDay.minByOrNull { timeOfDayMins(it) }?.let { timeFmt.format(Date(it)) }
    val firstLatest = firstDosesByDay.maxByOrNull { timeOfDayMins(it) }?.let { timeFmt.format(Date(it)) }

    // Missed days in the last 30 (off-days excluded).
    val offDays = medication?.offDays.orEmpty().toSet()
    val missedDays = (0 until 30).mapNotNull { daysAgo ->
        val dayStart = startOfDay(System.currentTimeMillis() - daysAgo * oneDayMillis)
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow in offDays) return@mapNotNull null
        val dosed = eventsByDay[dayStart]?.any { it.action in confirmedActions } == true
        if (!dosed) dayStart else null
    }

    // Top tags from notes (notes can be a comma-separated string or freetext).
    val topTags = recent
        .mapNotNull { it.note }
        .flatMap { it.split(",", ";").map(String::trim).filter(String::isNotBlank) }
        .groupingBy { it.lowercase(Locale.getDefault()) }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(5)
        .map { it.key to it.value }

    return ReportData(
        medication = medication,
        events = allEvents,
        adherence30Days = taken30.toFloat() / 30f,
        adherenceWeekdays = if (wkdTotal > 0) wkdDosed.toFloat() / wkdTotal else 0f,
        adherenceWeekends = if (wkeTotal > 0) wkeDosed.toFloat() / wkeTotal else 0f,
        streak = streak,
        totalDoses = allEvents.count { it.action in confirmedActions },
        firstDoseMedian = firstMedian,
        firstDoseEarliest = firstEarliest,
        firstDoseLatest = firstLatest,
        missedDays = missedDays,
        topTags = topTags,
    )
}

private fun printReport(context: Context, data: ReportData) {
    val medication = data.medication ?: return
    val fmt = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    val confirmedActions = setOf(DoseAction.INITIAL_TAP, DoseAction.TAKEN)
    val cutoff30 = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000

    val doseRows = data.events
        .filter { it.action in confirmedActions && it.actionedAt >= cutoff30 }
        .sortedByDescending { it.actionedAt }
        .joinToString("") { event ->
            "<tr><td>${fmt.format(Date(event.actionedAt))}</td><td>${event.action.name.replace('_', ' ')}</td></tr>"
        }

    val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
    val missedRows = data.missedDays.joinToString(", ") { dateFmt.format(Date(it)) }
        .ifBlank { "None" }
    val firstDoseRange = when {
        data.firstDoseMedian == null -> "—"
        data.firstDoseEarliest != null && data.firstDoseLatest != null ->
            "usually ${data.firstDoseMedian}, range ${data.firstDoseEarliest}–${data.firstDoseLatest}"
        else -> data.firstDoseMedian!!
    }
    val tagsBlock = if (data.topTags.isEmpty()) "No tagged notes in this period."
    else "<ul style=\"margin:0;padding-left:18px\">" +
        data.topTags.joinToString("") { (tag, count) ->
            "<li>${tag.replace("<", "&lt;")} (${count}x)</li>"
        } + "</ul>"

    val html = """
        <!DOCTYPE html><html><head><meta charset="UTF-8"/>
        <style>
          body{font-family:Arial,sans-serif;font-size:12px;margin:20px;color:#222}
          h1{font-size:18px;margin-bottom:4px}
          h2{font-size:14px;margin-top:20px;margin-bottom:8px;color:#333}
          table{width:100%;border-collapse:collapse;margin-top:8px}
          th,td{border:1px solid #ccc;padding:6px 10px;text-align:left}
          th{background:#f5f5f5}
          .stat{display:inline-block;margin-right:24px;margin-bottom:4px}
          .generated{color:#888;font-size:11px}
          .footer{margin-top:28px;font-size:10px;color:#999;font-style:italic;border-top:1px solid #eee;padding-top:8px}
        </style></head><body>
        <h1>Medication Adherence Report</h1>
        <p class="generated">Generated ${SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())} · 30-day window</p>

        <h2>Medication</h2>
        <p><b>${medication.name}</b>${if (medication.durationMinutes != null) " — effect window approx. ${medication.durationMinutes / 60}h ${medication.durationMinutes % 60}min" else ""}</p>

        <h2>Adherence</h2>
        <p>
          <span class="stat"><b>30-day overall:</b> ${(data.adherence30Days * 100).toInt()}%</span>
          <span class="stat"><b>Weekdays:</b> ${(data.adherenceWeekdays * 100).toInt()}%</span>
          <span class="stat"><b>Weekends:</b> ${(data.adherenceWeekends * 100).toInt()}%</span>
        </p>
        <p>
          <span class="stat"><b>Total doses logged:</b> ${data.totalDoses}</span>
        </p>

        <h2>Time of first dose</h2>
        <p>$firstDoseRange</p>

        <h2>Days without a logged dose (last 30)</h2>
        <p>${data.missedDays.size} day(s)${if (data.missedDays.isNotEmpty()) ": $missedRows" else ""}</p>

        <h2>Most common notes</h2>
        $tagsBlock

        <h2>Dose Log (Last 30 Days)</h2>
        <table><tr><th>Date &amp; Time</th><th>Action</th></tr>$doseRows</table>

        <p class="footer">Generated by DopaTap. Data, not interpretation. This document is a record of self-logged dose timings and is not a medical record or diagnostic tool.</p>
        </body></html>
    """.trimIndent()

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "DopaTap Report ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}"
            val printAdapter = view.createPrintDocumentAdapter(jobName)
            printManager.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder().build(),
            )
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
