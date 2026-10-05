package com.example.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TimeSyncState
import com.example.engine.StopwatchEngine
import com.example.engine.StopwatchState
import com.example.engine.StopwatchStatus
import com.example.ui.components.ChronometerDisplay
import com.example.ui.components.LapList
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardNavy
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceNavy
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun StopwatchScreen(
    stopwatchState: StopwatchState,
    syncState: TimeSyncState,
    stopwatchEngine: StopwatchEngine,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Handle Android system back gesture/button
    BackHandler {
        onNavigateBack()
    }

    val scope = rememberCoroutineScope()
    var touchRippleVisible by remember { mutableStateOf(false) }
    var touchRippleOffset by remember { mutableStateOf(Offset.Zero) }
    var lastTapLatencyNanos by remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VoidBlack,
                        DarkNavy,
                        VoidBlack
                    )
                )
            )
            // Ultra-low latency full-screen down event pointer input
            // Tap ANYWHERE on the screen to start, tap ANYWHERE to stop!
            .pointerInput(stopwatchState.status) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downNanos = SystemClock.elapsedRealtimeNanos()
                    touchRippleOffset = down.position
                    touchRippleVisible = true

                    // Trigger stopwatch immediately at nanosecond resolution
                    stopwatchEngine.onFullScreenTap(downNanos)

                    val dispatchEnd = SystemClock.elapsedRealtimeNanos()
                    lastTapLatencyNanos = dispatchEnd - downNanos

                    scope.launch {
                        delay(250)
                        touchRippleVisible = false
                    }
                }
            }
            .testTag("stopwatch_fullscreen_tap_target")
    ) {
        // Visual touch feedback ripple at point of contact
        if (touchRippleVisible) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (touchRippleOffset.x - 40.dp.toPx()).toInt(),
                            (touchRippleOffset.y - 40.dp.toPx()).toInt()
                        )
                    }
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.25f))
                    .border(2.dp, NeonCyan, CircleShape)
            )
        }

        // Main Content Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Navigation & Server Ping Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardNavy)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .testTag("stopwatch_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = TextPrimary
                    )
                }

                // Authoritative NTP Server Sync Indicator
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardNavy)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (syncState.isSynced) NeonEmerald else NeonAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PING: ${syncState.pingMs}ms",
                        color = if (syncState.pingMs < 30) NeonEmerald else NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = syncState.serverName,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                // Touch Engine Latency Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardNavy)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (stopwatchState.lastTouchLatencyMicros > 0)
                            String.format(Locale.US, "%.1f ms", stopwatchState.lastTouchLatencyMicros / 1000f)
                        else "< 1 ms",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Center: Big Chronometer Digits & State Prompt
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Numbers in Center: MM : SS . mmm
                ChronometerDisplay(
                    minutes = stopwatchState.minutes,
                    seconds = stopwatchState.seconds,
                    millis = stopwatchState.millis,
                    micros = stopwatchState.micros,
                    status = stopwatchState.status
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Prominent Instruction Banner: Tap anywhere to start / stop
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when (stopwatchState.status) {
                                StopwatchStatus.RUNNING -> NeonCrimson.copy(alpha = 0.15f)
                                StopwatchStatus.STOPPED -> NeonAmber.copy(alpha = 0.15f)
                                StopwatchStatus.IDLE -> NeonCyan.copy(alpha = 0.12f)
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = when (stopwatchState.status) {
                                StopwatchStatus.RUNNING -> NeonCrimson.copy(alpha = 0.4f)
                                StopwatchStatus.STOPPED -> NeonAmber.copy(alpha = 0.4f)
                                StopwatchStatus.IDLE -> NeonCyan.copy(alpha = 0.3f)
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = when (stopwatchState.status) {
                                StopwatchStatus.RUNNING -> NeonCrimson
                                StopwatchStatus.STOPPED -> NeonAmber
                                StopwatchStatus.IDLE -> NeonCyan
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (stopwatchState.status) {
                                StopwatchStatus.IDLE -> "แตะที่ไหนก็ได้บนหน้าจอเพื่อเริ่มจับเวลา"
                                StopwatchStatus.RUNNING -> "แตะที่ไหนก็ได้บนหน้าจอเพื่อหยุดจับเวลา"
                                StopwatchStatus.STOPPED -> "แตะที่ไหนก็ได้เพื่อจับเวลาต่อ หรือกดรีเซ็ต"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (stopwatchState.status) {
                                StopwatchStatus.RUNNING -> NeonCrimson
                                StopwatchStatus.STOPPED -> NeonAmber
                                StopwatchStatus.IDLE -> NeonCyan
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Bottom Section: Lap List & Reset/Lap Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lap Times List
                LapList(
                    laps = stopwatchState.laps,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Secondary Action Buttons: LAP and RESET
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // LAP Button (Active when RUNNING)
                    Button(
                        onClick = { stopwatchEngine.recordLap() },
                        enabled = stopwatchState.status == StopwatchStatus.RUNNING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceNavy,
                            disabledContainerColor = CardNavy.copy(alpha = 0.5f),
                            contentColor = NeonCyan,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("lap_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Lap",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LAP (รอบ)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // RESET Button (Active when STOPPED or IDLE with elapsed time)
                    Button(
                        onClick = { stopwatchEngine.reset() },
                        enabled = stopwatchState.elapsedNanos > 0L && stopwatchState.status != StopwatchStatus.RUNNING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (stopwatchState.status == StopwatchStatus.STOPPED) NeonAmber.copy(alpha = 0.2f) else CardNavy,
                            disabledContainerColor = CardNavy.copy(alpha = 0.5f),
                            contentColor = if (stopwatchState.status == StopwatchStatus.STOPPED) NeonAmber else TextMuted,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RESET (รีเซ็ต)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
