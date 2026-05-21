package com.dosetap.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallBottomSheet(
    price: String,
    onPurchase: (Activity) -> Unit,
    onRestore: () -> Unit,
    onGiftCode: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showGiftCodeField by remember { mutableStateOf(false) }
    var giftCodeText by remember { mutableStateOf("") }
    var giftCodeError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Crown icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⭐", fontSize = 28.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "DoseTap Pro",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "One-time · $price · No subscription ever",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            // Feature list
            val features = listOf(
                Triple(Icons.Filled.Schedule, "Up to 4 follow-up reminders", "Free includes 2"),
                Triple(Icons.Filled.CalendarMonth, "Off-days", "Skip reminders on weekends or chosen days"),
                Triple(Icons.Filled.Notifications, "Daily proactive reminder", "\"You haven't logged yet\" nudge"),
                Triple(Icons.Filled.AccessTime, "Effect window tracker", "See exactly when your dose wears off"),
                Triple(Icons.Filled.LocalPharmacy, "Pill count & refill alerts", "Know before you run out"),
                Triple(Icons.Filled.BarChart, "Full adherence chart", "14-day visual + weekly summary"),
                Triple(Icons.Filled.MusicNote, "Custom alarm tone", "Use any sound on your phone"),
                Triple(Icons.Filled.FileUpload, "Export & import backup", "Switch phones without losing history"),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                features.forEach { (icon, title, subtitle) ->
                    ProFeatureRow(icon = icon, title = title, subtitle = subtitle)
                }
            }

            Spacer(Modifier.height(28.dp))

            // CTA button
            Button(
                onClick = { context.findActivity()?.let(onPurchase) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = "Unlock Pro — $price",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onRestore) {
                    Text(
                        text = "Restore purchase",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = { showGiftCodeField = !showGiftCodeField }) {
                    Text(
                        text = "Gift code",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Gift code field
            if (showGiftCodeField) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = giftCodeText,
                        onValueChange = {
                            giftCodeText = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }
                            giftCodeError = false
                        },
                        label = { Text("Enter gift code") },
                        isError = giftCodeError,
                        supportingText = if (giftCodeError) {
                            { Text("Invalid or already used code") }
                        } else null,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            if (onGiftCode(giftCodeText)) {
                                scope.launch { sheetState.hide(); onDismiss() }
                            } else {
                                giftCodeError = true
                            }
                        },
                        enabled = giftCodeText.length >= 8,
                    ) {
                        Text("Redeem")
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun ProFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(18.dp),
        )
    }
}
