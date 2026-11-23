// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.status.StatusRepository
import io.github.rhjihan.slackwake.ui.components.PrerequisiteList
import io.github.rhjihan.slackwake.ui.theme.StatusColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val status: StatusRepository,
    ) : ViewModel() {
        val prerequisites = status.prerequisites

        val slackConfirmed =
            settings.settings
                .map { it.slackNotificationsConfirmed }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        fun setSlackConfirmed(v: Boolean) {
            viewModelScope.launch { settings.update { it.copy(slackNotificationsConfirmed = v) } }
        }

        fun refresh() = status.refreshPrerequisites()

        /** Completes onboarding; [keepFlash] is the answer to the one-time photosensitivity warning. */
        fun finish(keepFlash: Boolean) {
            viewModelScope.launch { settings.update { it.copy(onboardingCompleted = true, flashEnabled = keepFlash) } }
        }
    }

private const val STEPS = 3

/**
 * First run: what SlackWake does → every prerequisite with deep links (listener access,
 * POST_NOTIFICATIONS, full-screen intent, battery, Slack's own settings) → the
 * photosensitivity warning, shown once here because flash is on by default.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val prereqs by viewModel.prerequisites.collectAsStateWithLifecycle()
    val slackConfirmed by viewModel.slackConfirmed.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                stringResource(R.string.onboarding_step, step + 1, STEPS),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (step + 1) / STEPS.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (step) {
                    0 -> {
                        Icon(
                            painterResource(R.drawable.ic_bell_armed),
                            contentDescription = null,
                            tint = StatusColors.Armed,
                            modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally),
                        )
                        Title(stringResource(R.string.onboarding_welcome_title))
                        Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge)
                    }

                    1 -> {
                        Title(stringResource(R.string.onboarding_setup_title))
                        PrerequisiteList(prereqs, slackConfirmed, viewModel::setSlackConfirmed)
                    }

                    else -> {
                        Title(stringResource(R.string.photosensitivity_title))
                        Text(stringResource(R.string.photosensitivity_body), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) { Text(stringResource(R.string.onboarding_back)) }
                } else {
                    TextButton(onClick = {
                        step = STEPS - 1
                    }, modifier = Modifier.testTag("onboarding_skip")) { Text(stringResource(R.string.onboarding_skip)) }
                }
                if (step < STEPS - 1) {
                    Button(
                        onClick = { step++ },
                        modifier = Modifier.testTag("onboarding_next"),
                    ) { Text(stringResource(R.string.onboarding_next)) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.finish(keepFlash = false) }, modifier = Modifier.testTag("flash_off")) {
                            Text(stringResource(R.string.photosensitivity_turn_off))
                        }
                        Button(onClick = { viewModel.finish(keepFlash = true) }, modifier = Modifier.testTag("flash_keep")) {
                            Text(stringResource(R.string.photosensitivity_keep_on))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}
