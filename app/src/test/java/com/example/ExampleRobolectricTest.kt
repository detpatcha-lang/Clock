package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LapItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Overclock", appName)
  }

  @Test
  fun `test lap time formatting`() {
    // 1 minute, 23 seconds, 456 milliseconds = 83,456,000,000 nanoseconds
    val nanos = (83L * 1000L + 456L) * 1_000_000L
    val formatted = LapItem.formatNanos(nanos)
    assertEquals("01:23.456", formatted)
  }
}
