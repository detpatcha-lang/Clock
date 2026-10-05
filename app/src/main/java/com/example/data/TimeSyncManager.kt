package com.example.data

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class TimeSyncState(
    val isSynced: Boolean = false,
    val isSyncing: Boolean = false,
    val serverName: String = "time.google.com",
    val pingMs: Long = 0L,
    val offsetMs: Long = 0L,
    val jitterMs: Double = 0.0,
    val syncMethod: String = "SNTP (UDP:123)",
    val lastSyncTimestamp: Long = 0L,
    val totalSyncCount: Int = 0,
    val hardwareClockResolutionNanos: Long = 1L
)

class TimeSyncManager private constructor() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val _syncState = MutableStateFlow(TimeSyncState())
    val syncState: StateFlow<TimeSyncState> = _syncState.asStateFlow()

    private var serverTimeAnchorMs: Long = 0L
    private var localMonotonicAnchorNanos: Long = 0L
    private var prevPing: Long = -1L

    private val ntpServers = listOf(
        "time.google.com",
        "time.cloudflare.com",
        "pool.ntp.org"
    )

    init {
        // Start continuous background sync loop to keep ping and offset accurate
        scope.launch {
            while (isActive) {
                syncTime()
                delay(15_000) // Re-sync every 15 seconds for live ping telemetry
            }
        }
    }

    /**
     * Get authoritative server time in milliseconds anchored to hardware monotonic clock
     */
    fun getAuthoritativeTimeMs(): Long {
        return if (serverTimeAnchorMs > 0 && localMonotonicAnchorNanos > 0) {
            val elapsedNanos = SystemClock.elapsedRealtimeNanos() - localMonotonicAnchorNanos
            serverTimeAnchorMs + (elapsedNanos / 1_000_000L)
        } else {
            System.currentTimeMillis()
        }
    }

    /**
     * Get current monotonic hardware nanoseconds with maximum precision
     */
    fun getMonotonicNanos(): Long {
        return SystemClock.elapsedRealtimeNanos()
    }

    suspend fun syncTime() = withContext(Dispatchers.IO) {
        _syncState.value = _syncState.value.copy(isSyncing = true)

        var success = false

        // Attempt 1: True SNTP UDP queries
        for (server in ntpServers) {
            try {
                val result = querySntp(server)
                if (result != null) {
                    val (ping, offset) = result
                    val jitter = if (prevPing >= 0) abs(ping - prevPing) * 0.5 else 1.0
                    prevPing = ping

                    serverTimeAnchorMs = System.currentTimeMillis() + offset
                    localMonotonicAnchorNanos = SystemClock.elapsedRealtimeNanos()

                    _syncState.value = TimeSyncState(
                        isSynced = true,
                        isSyncing = false,
                        serverName = server,
                        pingMs = ping,
                        offsetMs = offset,
                        jitterMs = jitter,
                        syncMethod = "SNTP (UDP:123)",
                        lastSyncTimestamp = System.currentTimeMillis(),
                        totalSyncCount = _syncState.value.totalSyncCount + 1,
                        hardwareClockResolutionNanos = 1L
                    )
                    success = true
                    break
                }
            } catch (e: Exception) {
                Log.d("TimeSyncManager", "SNTP failed for $server: ${e.message}")
            }
        }

        // Attempt 2: High-speed HTTP Round-Trip Ping fallback if UDP 123 blocked
        if (!success) {
            try {
                val httpResult = queryHttpPing("https://time.google.com")
                if (httpResult != null) {
                    val (ping, offset) = httpResult
                    val jitter = if (prevPing >= 0) abs(ping - prevPing) * 0.5 else 1.5
                    prevPing = ping

                    serverTimeAnchorMs = System.currentTimeMillis() + offset
                    localMonotonicAnchorNanos = SystemClock.elapsedRealtimeNanos()

                    _syncState.value = TimeSyncState(
                        isSynced = true,
                        isSyncing = false,
                        serverName = "time.google.com",
                        pingMs = ping,
                        offsetMs = offset,
                        jitterMs = jitter,
                        syncMethod = "HTTP-Ping (TCP/TLS)",
                        lastSyncTimestamp = System.currentTimeMillis(),
                        totalSyncCount = _syncState.value.totalSyncCount + 1,
                        hardwareClockResolutionNanos = 1L
                    )
                    success = true
                }
            } catch (e: Exception) {
                Log.d("TimeSyncManager", "HTTP sync fallback failed: ${e.message}")
            }
        }

        if (!success) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                syncMethod = "Hardware Monotonic (Local)"
            )
        }
    }

    /**
     * SNTP client query via standard UDP port 123 (RFC 4330 / RFC 5905)
     * Returns Pair<PingMs, OffsetMs>
     */
    private fun querySntp(serverHost: String): Pair<Long, Long>? {
        var socket: DatagramSocket? = null
        try {
            val address = InetAddress.getByName(serverHost)
            socket = DatagramSocket().apply {
                soTimeout = 2500
            }

            val buffer = ByteArray(48)
            // Mode 3 (Client), Version 3 (0x1B) or Version 4 (0x23)
            buffer[0] = 0x23

            val t1 = System.currentTimeMillis()
            writeTimestamp(buffer, 40, t1)

            val requestPacket = DatagramPacket(buffer, buffer.size, address, 123)
            socket.send(requestPacket)

            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)
            val t4 = System.currentTimeMillis()

            val t2 = readTimestamp(buffer, 32) // Server Receive
            val t3 = readTimestamp(buffer, 40) // Server Transmit

            // Round trip delay = (T4 - T1) - (T3 - T2)
            val roundTrip = (t4 - t1) - (t3 - t2)
            val ping = if (roundTrip >= 0) roundTrip else (t4 - t1)
            // Clock offset = ((T2 - T1) + (T3 - T4)) / 2
            val offset = ((t2 - t1) + (t3 - t4)) / 2

            return Pair(ping.coerceAtLeast(1L), offset)
        } catch (e: Exception) {
            return null
        } finally {
            socket?.close()
        }
    }

    private fun queryHttpPing(url: String): Pair<Long, Long>? {
        val request = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "Overclock-HighPrecisionEngine/1.0")
            .build()

        val startNanos = SystemClock.elapsedRealtimeNanos()
        val startLocalTime = System.currentTimeMillis()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val endNanos = SystemClock.elapsedRealtimeNanos()
                val pingMs = ((endNanos - startNanos) / 1_000_000L).coerceAtLeast(1L)

                val dateHeader = response.header("Date")
                var offsetMs = 0L
                if (dateHeader != null) {
                    try {
                        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("GMT")
                        }
                        val serverDate = format.parse(dateHeader)
                        if (serverDate != null) {
                            val serverTime = serverDate.time + (pingMs / 2)
                            offsetMs = serverTime - (startLocalTime + pingMs / 2)
                        }
                    } catch (_: Exception) { }
                }

                Pair(pingMs, offsetMs)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeTimestamp(buffer: ByteArray, offset: Int, time: Long) {
        val seconds = time / 1000L + 2208988800L // Epoch offset 1900 vs 1970
        val fraction = ((time % 1000L) * 0x100000000L) / 1000L

        for (i in 3 downTo 0) {
            buffer[offset + i] = (seconds shr ((3 - i) * 8)).toByte()
        }
        for (i in 3 downTo 0) {
            buffer[offset + 4 + i] = (fraction shr ((3 - i) * 8)).toByte()
        }
    }

    private fun readTimestamp(buffer: ByteArray, offset: Int): Long {
        var seconds = 0L
        for (i in 0..3) {
            seconds = (seconds shl 8) or (buffer[offset + i].toLong() and 0xFFL)
        }
        var fraction = 0L
        for (i in 4..7) {
            fraction = (fraction shl 8) or (buffer[offset + i].toLong() and 0xFFL)
        }
        val ms = ((seconds - 2208988800L) * 1000L) + ((fraction * 1000L) / 0x100000000L)
        return ms
    }

    companion object {
        @Volatile
        private var instance: TimeSyncManager? = null

        fun getInstance(): TimeSyncManager {
            return instance ?: synchronized(this) {
                instance ?: TimeSyncManager().also { instance = it }
            }
        }
    }
}
