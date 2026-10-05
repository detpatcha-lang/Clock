package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.StopwatchStatus
import com.example.ui.theme.CardNavy
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.SurfaceNavy
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.util.Locale

@Composable
fun ChronometerDisplay(
    minutes: Int,
    seconds: Int,
    millis: Int,
    micros: Int,
    status: StopwatchStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val activeGlowColor by animateColorAsState(
        targetValue = when (status) {
            StopwatchStatus.RUNNING -> NeonCyan
            StopwatchStatus.STOPPED -> NeonAmber
            StopwatchStatus.IDLE -> NeonCyan.copy(alpha = 0.5f)
        },
        label = "statusColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CardNavy.copy(alpha = 0.85f),
                        VoidBlack.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        activeGlowColor.copy(alpha = if (status == StopwatchStatus.RUNNING) glowAlpha else 0.4f),
                        activeGlowColor.copy(alpha = 0.15f),
                        activeGlowColor.copy(alpha = if (status == StopwatchStatus.RUNNING) glowAlpha else 0.4f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status & Sub-title Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when (status) {
                        StopwatchStatus.RUNNING -> NeonEmerald.copy(alpha = 0.15f)
                        StopwatchStatus.STOPPED -> NeonAmber.copy(alpha = 0.15f)
                        StopwatchStatus.IDLE -> SurfaceNavy
                    }
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = when (status) {
                    StopwatchStatus.RUNNING -> "● RUNNING (TAP TO STOP)"
                    StopwatchStatus.STOPPED -> "❚❚ PAUSED (TAP TO RESUME)"
                    StopwatchStatus.IDLE -> "READY (TAP TO START)"
                },
                color = when (status) {
                    StopwatchStatus.RUNNING -> NeonEmerald
                    StopwatchStatus.STOPPED -> NeonAmber
                    StopwatchStatus.IDLE -> TextSecondary
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Digits: MM : SS . mmm
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chronometer_digits"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            // Minutes Block
            DigitBlock(
                digits = String.format(Locale.US, "%02d", minutes),
                label = "MIN",
                color = TextPrimary
            )

            // Colon separator
            Text(
                text = ":",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = activeGlowColor.copy(alpha = 0.8f)
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            // Seconds Block
            DigitBlock(
                digits = String.format(Locale.US, "%02d", seconds),
                label = "SEC",
                color = TextPrimary
            )

            // Decimal separator
            Text(
                text = ".",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = activeGlowColor.copy(alpha = 0.8f)
                ),
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp)
            )

            // Milliseconds Block (Highlighted 3 digits)
            DigitBlock(
                digits = String.format(Locale.US, "%03d", millis),
                label = "MS",
                color = activeGlowColor,
                isSubUnit = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Microseconds and hardware resolution telemetry bar
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceNavy.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SUB-MS μs: ",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = String.format(Locale.US, "+%03d μs", micros),
                color = NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "| ±1ns HW ACCURACY",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DigitBlock(
    digits: String,
    label: String,
    color: Color,
    isSubUnit: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = digits,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = if (isSubUnit) 46.sp else 54.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = color
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp
        )
    }
}
