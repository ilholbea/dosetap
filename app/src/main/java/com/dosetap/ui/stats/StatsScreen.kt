package com.dosetap.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetap.DoseTapApp
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container

    val viewModel: StatsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return StatsViewModel(container) as T
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPro by container.isPro.collectAsStateWithLifecycle()
    var showPaywall by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showPaywall) {
        com.dosetap.ui.paywall.PaywallBottomSheet(
            price = container.billingManager.formattedPrice(),
            onPurchase = { activity -> container.billingManager.launchPurchase(activity) },
            onRestore = { container.billingManager.restorePurchases() },
            onGiftCode = { code ->
                when (val result = container.giftCodeManager.validate(code)) {
                    com.dosetap.billing.GiftCodeManager.CodeResult.DevGrant -> {
                        (context.applicationContext as com.dosetap.DoseTapApp).appScope.launch {
                            container.settingsRepository.setDevGrantUnlocked()
                        }
                        result
                    }
                    com.dosetap.billing.GiftCodeManager.CodeResult.PermanentGift -> {
                        (context.applicationContext as com.dosetap.DoseTapApp).appScope.launch {
                            container.settingsRepository.setPermanentGiftUnlocked()
                        }
                        result
                    }
                    com.dosetap.billing.GiftCodeManager.CodeResult.SponsorGift -> {
                        (context.applicationContext as com.dosetap.DoseTapApp).appScope.launch {
                            container.settingsRepository.startTrial("gift")
                        }
                        result
                    }
                    com.dosetap.billing.GiftCodeManager.CodeResult.Invalid -> null
                }
            },
            onDismiss = { showPaywall = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Adherence Stats",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            !state.hasAnyData -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    ) {
                        Text("📊", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(12.dp))
                        Text("No stats yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Log 3 doses to unlock your first adherence stats. Tap the 💊 button on Home or your home-screen widget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Milestone celebration card — shown once per milestone, session-local dismiss
                    val milestoneStreak = state.milestoneStreak
                    if (milestoneStreak != null) {
                        item {
                            var milestoneDismissed by remember(milestoneStreak) { mutableStateOf(false) }
                            AnimatedVisibility(
                                visible = !milestoneDismissed,
                                enter = slideInVertically(tween(400)) + fadeIn(tween(400)),
                                exit = slideOutVertically(tween(300)) + fadeOut(tween(300)),
                            ) {
                                MilestoneCard(
                                    streakDays = milestoneStreak,
                                    onDismiss = { milestoneDismissed = true },
                                )
                            }
                        }
                    }
                    // Adherence leads — the surviving metric even when a streak resets (Spec #1).
                    item { AdherenceRow(state) }
                    item { StreakCard(state) }
                    if (isPro) {
                        item { HeatmapCard(state.last90Days) }
                    } else {
                        item { ChartProGate(onUnlock = { showPaywall = true }) }
                    }
                    item { TotalDosesCard(state.totalDoses) }
                }
            }
        }
    }
}

private fun streakTint(days: Int): Color = when {
    days >= 90 -> Color(0xFFFFB300)   // gold
    days >= 30 -> Color(0xFFF4511E)   // deep orange
    days >= 7  -> Color(0xFFFB8C00)   // orange
    days >= 3  -> Color(0xFFFFB74D)   // soft amber
    else       -> Color(0xFF4CAF50)   // calm green
}

private fun streakEmoji(days: Int): String = when {
    days >= 90 -> "⭐"
    days >= 7  -> "🔥"
    else       -> "💊"
}

@Composable
private fun StreakCard(state: StatsUiState) {
    val count = state.currentStreak
    val streakAlive = count > 0
    val isPersonalBest = state.todayLogged && count >= 3 && count >= state.bestStreak
    // Filled flame once today is logged; hollow (dimmed) while today is still open.
    val flameAlpha = if (state.todayLogged) 1f else 0.5f

    // Subtext follows the streak state (Spec #1). Never leads with a broken-chain framing.
    val subtext: String? = when {
        state.todayIsBridge -> "Off day — streak safe"
        streakAlive && !state.todayLogged -> "Day $count — today still open"
        !streakAlive && !state.todayLogged -> "Streak restarting — day 1 is today"
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimatedContent(
                        targetState = count,
                        transitionSpec = {
                            slideInVertically { -it } + fadeIn() togetherWith
                                slideOutVertically { it } + fadeOut()
                        },
                        label = "streak_count",
                    ) { c ->
                        Text(
                            text = "${streakEmoji(c)} $c day${if (c != 1) "s" else ""}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alpha(flameAlpha),
                        )
                    }
                }
                if (subtext != null) {
                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.graceApplied) {
                    Text(
                        text = "Streak bridged — one off day doesn't undo $count.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isPersonalBest) {
                    Text(
                        text = "Personal best ✨",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Best",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${state.bestStreak}d",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MilestoneCard(streakDays: Int, onDismiss: () -> Unit) {
    val copy = when (streakDays) {
        7    -> "7 days strong 🔥"
        30   -> "30-day milestone 🎯"
        90   -> "90-day milestone 🏆"
        180  -> "Half a year of consistency ✨"
        365  -> "One full year ✨"
        else -> "$streakDays days 🎉"
    }
    val tint = streakTint(streakDays)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = copy,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )
                Text(
                    text = "Keep going — your brain is building a real habit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tint.copy(alpha = 0.75f),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = tint.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AdherenceRow(state: StatsUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AdherenceCard(
            label = "7-day",
            value = state.adherence7Days,
            modifier = Modifier.weight(1f),
        )
        AdherenceCard(
            label = "30-day",
            value = state.adherence30Days,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AdherenceCard(label: String, value: Float, modifier: Modifier = Modifier) {
    val pct = (value * 100).roundToInt()
    val color = when {
        pct >= 80 -> Color(0xFF2E7D32) // green
        pct >= 50 -> Color(0xFFE65100) // amber/orange
        else -> MaterialTheme.colorScheme.error
    }
    val containerColor = when {
        pct >= 80 -> Color(0xFFE8F5E9)
        pct >= 50 -> Color(0xFFFFF3E0)
        else -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f),
            )
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = "adherence",
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun HeatmapCard(days: List<DayStats>) {
    // days is oldest-first (0..89 daysAgo = up to 90 entries)
    // Pad the start so the oldest entry lands on the correct day-of-week row (Sun = row 0).
    val leadingPad = if (days.isEmpty()) 0 else {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() - (days.size - 1).toLong() * 86_400_000L
        }
        cal.get(java.util.Calendar.DAY_OF_WEEK) - 1  // 1=Sun..7=Sat → 0-indexed
    }
    val paddedDays: List<DayStats?> = buildList {
        repeat(leadingPad) { add(null) }
        addAll(days)
        // Fill trailing cells to complete the last column
        val remainder = size % 7
        if (remainder != 0) repeat(7 - remainder) { add(null) }
    }
    // Columns = weeks (oldest left), rows = days Sun..Sat
    val weekColumns = paddedDays.chunked(7)

    val takenColor = MaterialTheme.colorScheme.secondary
    val missedColor = MaterialTheme.colorScheme.error
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val rowLabels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "90-day history",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // Day-of-week labels column
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    rowLabels.forEach { label ->
                        Box(
                            modifier = Modifier.size(width = 18.dp, height = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 7.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                weekColumns.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day ->
                            val color = when {
                                day == null -> Color.Transparent
                                day.takenCount > 0 -> takenColor
                                day.missedCount > 0 || day.skippedCount > 0 -> missedColor
                                else -> emptyColor
                            }
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem(color = takenColor, label = "Taken")
                LegendItem(color = missedColor, label = "Missed")
                LegendItem(color = emptyColor, label = "No data")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChartProGate(onUnlock: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📊", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "90-day adherence heatmap",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "See your full 3-month history at a glance — available with Pro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = onUnlock) {
                Text("⭐  Unlock Pro", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TotalDosesCard(total: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total doses logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "All time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Text(
                text = "$total",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
