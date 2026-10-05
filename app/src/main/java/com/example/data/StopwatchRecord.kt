package com.example.data

data class LapItem(
    val lapNumber: Int,
    val lapTimeNanos: Long,
    val splitTimeNanos: Long,
    val touchLatencyMicros: Long = 850L, // Microseconds touch response
    val isFastest: Boolean = false,
    val isSlowest: Boolean = false
) {
    val formattedLapTime: String get() = formatNanos(lapTimeNanos)
    val formattedSplitTime: String get() = formatNanos(splitTimeNanos)

    companion object {
        fun formatNanos(nanos: Long): String {
            val totalMillis = nanos / 1_000_000L
            val minutes = (totalMillis / 60_000L) % 60
            val seconds = (totalMillis / 1_000L) % 60
            val millis = totalMillis % 1_000L
            return String.format(java.util.Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        }
    }
}

data class StopwatchSessionRecord(
    val id: Long = System.currentTimeMillis(),
    val totalTimeNanos: Long,
    val lapsCount: Int,
    val bestLapNanos: Long?,
    val timestamp: Long = System.currentTimeMillis(),
    val serverSource: String = "time.google.com",
    val pingMs: Long = 0L
) {
    val formattedTotalTime: String get() = LapItem.formatNanos(totalTimeNanos)
    val formattedBestLap: String get() = bestLapNanos?.let { LapItem.formatNanos(it) } ?: "-"
}
