package com.dosetap.ui.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetap.DoseTapApp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerScreen(
    onNavigateBack: () -> Unit,
    startMode: String? = null,
    autoStart: Boolean = false,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as DoseTapApp).container

    val viewModel: FocusTimerViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                FocusTimerViewModel(container, context) as T
        }
    )

    // Deep-linked entry: pre-select a mode and optionally auto-start (Spec #5 / #8).
    LaunchedEffect(Unit) {
        val mode = startMode?.let { runCatching { FocusMode.valueOf(it) }.getOrNull() }
        if (mode != null) {
            viewModel.selectMode(mode)
            if (autoStart) viewModel.start()
        }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Focus",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Mode selector chips
            ModeSelector(
                selectedMode = state.mode,
                isRunning = state.isRunning,
                onSelect = { viewModel.selectMode(it) },
            )

            Spacer(Modifier.height(32.dp))

            // The main ring + time display
            TimerRing(state = state)

            Spacer(Modifier.height(8.dp))

            // Sessions counter
            if (state.sessionsToday > 0) {
                Text(
                    text = "${state.sessionsToday} session${if (state.sessionsToday == 1) "" else "s"} today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Duration adjuster (only when idle)
            AnimatedVisibility(
                visible = !state.isRunning && !state.isPaused && !state.isComplete && !state.isCountUp,
            ) {
                DurationAdjuster(
                    durationSeconds = state.durationSeconds,
                    onAdjust = { delta -> viewModel.adjustDuration(delta * 60) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // Action buttons
            TimerControls(
                state = state,
                onStart = { viewModel.start() },
                onPause = { viewModel.pause() },
                onReset = { viewModel.reset() },
            )

            Spacer(Modifier.height(24.dp))

            // Task note input
            AnimatedVisibility(visible = state.isRunning || state.isPaused || state.isComplete) {
                OutlinedTextField(
                    value = state.taskNote,
                    onValueChange = { viewModel.updateTaskNote(it) },
                    label = { Text("What are you working on? (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Med window hint
            state.medWindowEndMillis?.let { endMillis ->
                MedWindowHint(endMillis = endMillis)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ModeSelector(
    selectedMode: FocusMode,
    isRunning: Boolean,
    onSelect: (FocusMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FocusMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            FilterChip(
                selected = selected,
                onClick = { if (!isRunning) onSelect(mode) },
                label = {
                    Text(
                        "${mode.emoji} ${mode.label}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = modeColor(mode).copy(alpha = 0.15f),
                    selectedLabelColor = modeColor(mode),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimerRing(state: FocusUiState) {
    val progressTarget = state.progress
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "ring_progress",
    )

    val ringColor = when {
        state.isComplete -> Color(0xFF43A047)
        state.isRunning && !state.isCountUp && state.remainingSeconds < state.durationSeconds * 0.10f -> Color(0xFFE53935)
        state.isRunning && !state.isCountUp && state.remainingSeconds < state.durationSeconds * 0.30f -> Color(0xFFFB8C00)
        else -> modeColor(state.mode)
    }
    val animatedRingColor by animateColorAsState(
        targetValue = ringColor,
        animationSpec = tween(500),
        label = "ring_color",
    )

    // Pulsing stroke width when running
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val strokePulse by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = if (state.isRunning && !state.isComplete) 20f else 16f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "stroke_pulse",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = if (state.isRunning && !state.isComplete) strokePulse else 16f
            val sw = stroke.dp.toPx()
            val inset = sw / 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(size.width - sw, size.height - sw)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )

            // Progress arc
            if (animatedProgress > 0.005f) {
                drawArc(
                    color = animatedRingColor,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
        }

        // Center content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.isComplete) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF43A047),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Done ✨",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF43A047),
                    fontWeight = FontWeight.Bold,
                )
            } else {
                AnimatedContent(
                    targetState = formatSeconds(state.displaySeconds),
                    transitionSpec = {
                        slideInVertically { -it } + fadeIn() togetherWith
                                slideOutVertically { it } + fadeOut()
                    },
                    label = "time",
                ) { timeStr ->
                    Text(
                        text = timeStr,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isRunning) animatedRingColor
                        else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        state.isCountUp -> "elapsed"
                        state.isPaused -> "paused"
                        state.isRunning -> "remaining"
                        else -> formatSeconds(state.durationSeconds)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DurationAdjuster(durationSeconds: Int, onAdjust: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = { onAdjust(-5) },
            modifier = Modifier.width(64.dp),
        ) { Text("−5") }

        Text(
            text = formatDuration(durationSeconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center,
        )

        FilledTonalButton(
            onClick = { onAdjust(+5) },
            modifier = Modifier.width(64.dp),
        ) { Text("+5") }
    }
}

@Composable
private fun TimerControls(
    state: FocusUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.isComplete -> {
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF43A047),
                    ),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New session", fontWeight = FontWeight.SemiBold)
                }
            }

            state.isRunning -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalButton(
                        onClick = onPause,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pause")
                    }
                    FilledTonalButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }

            else -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isPaused) "Resume" else "Start",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (state.isPaused) {
                    TextButton(onClick = onReset) {
                        Text("Reset", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MedWindowHint(endMillis: Long) {
    val now = System.currentTimeMillis()
    val remainingMin = ((endMillis - now) / 60_000L).toInt()

    // Don't show hint for already-expired windows
    if (remainingMin <= 0) return

    val (color, icon, label) = when {
        remainingMin > 60 -> Triple(Color(0xFF2E7D32), "🟢", "Medication active for ${remainingMin / 60}h ${remainingMin % 60}m")
        remainingMin > 15 -> Triple(Color(0xFFF57F17), "🟡", "Window ends in ${remainingMin}m — make it count")
        else -> Triple(Color(0xFFB71C1C), "🔴", "Window ending soon (${remainingMin}m)")
    }

    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$icon $label",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun animateColorAsState(
    targetValue: Color,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Color> = tween(300),
    label: String = "color",
): androidx.compose.runtime.State<Color> {
    return androidx.compose.animation.animateColorAsState(
        targetValue = targetValue,
        animationSpec = animationSpec,
        label = label,
    )
}

private fun modeColor(mode: FocusMode): Color = when (mode) {
    FocusMode.FOCUS -> Color(0xFF006A6A)
    FocusMode.TASK -> Color(0xFF3949AB)
    FocusMode.TRANSITION -> Color(0xFFE65100)
    FocusMode.BREAK -> Color(0xFF2E7D32)
}

private fun formatSeconds(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m} min"
    }
}
