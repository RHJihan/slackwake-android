// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.ui.onboarding.OnboardingScreen
import io.github.rhjihan.slackwake.ui.settings.SettingsRoute
import io.github.rhjihan.slackwake.ui.theme.SlackWakeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settings: SettingsRepository,
    ) : ViewModel() {
        /** null until settings have loaded, so the wrong screen never flashes up. */
        val onboardingCompleted =
            settings.settings
                .map { it.onboardingCompleted }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

/** Single activity for settings and onboarding (the alert has its own activity/task). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SlackWakeTheme {
                val main: MainViewModel = hiltViewModel()
                val done by main.onboardingCompleted.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize()) {
                    when (done) {
                        null -> Box(Modifier.fillMaxSize())
                        false -> OnboardingScreen(hiltViewModel())
                        true -> SettingsRoute(hiltViewModel())
                    }
                }
            }
        }
    }
}
