package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TimeSyncManager
import com.example.engine.StopwatchEngine
import com.example.engine.StopwatchStatus
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.StopwatchScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    STOPWATCH
}

class MainActivity : ComponentActivity() {

    private lateinit var timeSyncManager: TimeSyncManager
    private lateinit var stopwatchEngine: StopwatchEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on for chronometer precision
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        timeSyncManager = TimeSyncManager.getInstance()
        stopwatchEngine = StopwatchEngine(applicationContext, timeSyncManager)

        setContent {
            MyApplicationTheme {
                val syncState by timeSyncManager.syncState.collectAsStateWithLifecycle()
                val stopwatchState by stopwatchEngine.state.collectAsStateWithLifecycle()
                val history by stopwatchEngine.history.collectAsStateWithLifecycle()
                val coroutineScope = rememberCoroutineScope()

                var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen_transition",
                        modifier = Modifier.padding(innerPadding)
                    ) { screen ->
                        when (screen) {
                            AppScreen.HOME -> {
                                HomeScreen(
                                    syncState = syncState,
                                    history = history,
                                    onManualSync = {
                                        coroutineScope.launch {
                                            timeSyncManager.syncTime()
                                        }
                                    },
                                    onNavigateToStopwatch = {
                                        currentScreen = AppScreen.STOPWATCH
                                    }
                                )
                            }
                            AppScreen.STOPWATCH -> {
                                StopwatchScreen(
                                    stopwatchState = stopwatchState,
                                    syncState = syncState,
                                    stopwatchEngine = stopwatchEngine,
                                    onNavigateBack = {
                                        currentScreen = AppScreen.HOME
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
