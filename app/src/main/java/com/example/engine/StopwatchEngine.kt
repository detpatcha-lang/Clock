package com.example.engine

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.LapItem
import com.example.data.StopwatchSessionRecord
import com.example.data.TimeSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class StopwatchStatus {
    IDLE,
    RUNNING,
    STOPPED
}

data class StopwatchState(
    val status: StopwatchStatus = StopwatchStatus.IDLE,
    val elapsedNanos: Long = 0L,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val millis: Int = 0,
    val micros: Int = 0,
    val laps: List<LapItem> = emptyList(),
    val lastTouchLatencyMicros: Long = 0L,
    val tapCount: Int = 0,
    val currentLapStartNanos: Long = 0L
)

class StopwatchEngine(
    private val context: Context,
    private val timeSyncManager: TimeSyncManager = TimeSyncManager.getInstance()
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(StopwatchState())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<StopwatchSessionRecord>>(emptyList())
    val history: StateFlow<List<StopwatchSessionRecord>> = _history.asStateFlow()

    private var startAnchorNanos: Long = 0L
    private var accumulatedNanos: Long = 0L
    private var lapAnchorNanos: Long = 0L

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Ultra-fast full-screen tap handler triggered on down-event.
     * Records exact hardware timestamp from down event to eliminate touch slop delay.
     */
    fun onFullScreenTap(downTimestampNanos: Long = SystemClock.elapsedRealtimeNanos()) {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val touchLatencyMicros = ((nowNanos - downTimestampNanos) / 1000L).coerceAtLeast(120L)

        triggerHapticTick()

        when (_state.value.status) {
            StopwatchStatus.IDLE -> {
                startTiming(downTimestampNanos, touchLatencyMicros)
            }
            StopwatchStatus.RUNNING -> {
                stopTiming(downTimestampNanos, touchLatencyMicros)
            }
            StopwatchStatus.STOPPED -> {
                resumeTiming(downTimestampNanos, touchLatencyMicros)
            }
        }
    }

    private fun startTiming(startNanos: Long, touchLatencyMicros: Long) {
        startAnchorNanos = startNanos
        lapAnchorNanos = startNanos
        accumulatedNanos = 0L

        _state.value = _state.value.copy(
            status = StopwatchStatus.RUNNING,
            lastTouchLatencyMicros = touchLatencyMicros,
            tapCount = _state.value.tapCount + 1,
            laps = emptyList()
        )
        startTicker()
    }

    private fun resumeTiming(resumeNanos: Long, touchLatencyMicros: Long) {
        startAnchorNanos = resumeNanos
        lapAnchorNanos = resumeNanos

        _state.value = _state.value.copy(
            status = StopwatchStatus.RUNNING,
            lastTouchLatencyMicros = touchLatencyMicros,
            tapCount = _state.value.tapCount + 1
        )
        startTicker()
    }

    private fun stopTiming(stopNanos: Long, touchLatencyMicros: Long) {
        tickerJob?.cancel()
        tickerJob = null

        val currentSegmentNanos = stopNanos - startAnchorNanos
        accumulatedNanos += currentSegmentNanos

        updateCalculatedTime(accumulatedNanos)

        _state.value = _state.value.copy(
            status = StopwatchStatus.STOPPED,
            elapsedNanos = accumulatedNanos,
            lastTouchLatencyMicros = touchLatencyMicros,
            tapCount = _state.value.tapCount + 1
        )
    }

    fun recordLap() {
        if (_state.value.status != StopwatchStatus.RUNNING) return
        triggerHapticTick()

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val currentTotalElapsed = accumulatedNanos + (nowNanos - startAnchorNanos)
        val currentLapDuration = nowNanos - lapAnchorNanos
        lapAnchorNanos = nowNanos

        val currentLaps = _state.value.laps.toMutableList()
        val newLapNumber = currentLaps.size + 1

        val newLap = LapItem(
            lapNumber = newLapNumber,
            lapTimeNanos = currentLapDuration,
            splitTimeNanos = currentTotalElapsed,
            touchLatencyMicros = _state.value.lastTouchLatencyMicros
        )
        currentLaps.add(0, newLap) // Add at top for easy reading

        // Calculate fastest and slowest
        val fastestNanos = currentLaps.minOf { it.lapTimeNanos }
        val slowestNanos = currentLaps.maxOf { it.lapTimeNanos }

        val markedLaps = currentLaps.map { lap ->
            lap.copy(
                isFastest = currentLaps.size > 1 && lap.lapTimeNanos == fastestNanos,
                isSlowest = currentLaps.size > 1 && lap.lapTimeNanos == slowestNanos
            )
        }

        _state.value = _state.value.copy(laps = markedLaps)
    }

    fun reset() {
        tickerJob?.cancel()
        tickerJob = null

        // Save session if elapsed time > 0
        if (accumulatedNanos > 0L) {
            val syncInfo = timeSyncManager.syncState.value
            val bestLap = _state.value.laps.minOfOrNull { it.lapTimeNanos }
            val record = StopwatchSessionRecord(
                totalTimeNanos = accumulatedNanos,
                lapsCount = _state.value.laps.size,
                bestLapNanos = bestLap,
                serverSource = syncInfo.serverName,
                pingMs = syncInfo.pingMs
            )
            val updatedHistory = listOf(record) + _history.value.take(19)
            _history.value = updatedHistory
        }

        startAnchorNanos = 0L
        accumulatedNanos = 0L
        lapAnchorNanos = 0L

        _state.value = StopwatchState()
        triggerHapticTick()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val nowNanos = SystemClock.elapsedRealtimeNanos()
                val currentTotalNanos = accumulatedNanos + (nowNanos - startAnchorNanos)
                updateCalculatedTime(currentTotalNanos)
                // 10ms loop corresponds to 100fps tick rate for millisecond updates
                delay(10)
            }
        }
    }

    private fun updateCalculatedTime(totalNanos: Long) {
        val totalMillis = totalNanos / 1_000_000L
        val minutes = ((totalMillis / 60_000L) % 60).toInt()
        val seconds = ((totalMillis / 1_000L) % 60).toInt()
        val millis = (totalMillis % 1_000L).toInt()
        val micros = ((totalNanos / 1_000L) % 1_000L).toInt()

        _state.value = _state.value.copy(
            elapsedNanos = totalNanos,
            minutes = minutes,
            seconds = seconds,
            millis = millis,
            micros = micros
        )
    }

    private fun triggerHapticTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(15)
            }
        } catch (_: Exception) { }
    }
}
