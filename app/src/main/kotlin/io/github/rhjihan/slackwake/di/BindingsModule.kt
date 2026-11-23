// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rhjihan.slackwake.core.schedule.TimeZoneCatalog
import io.github.rhjihan.slackwake.data.timezone.IcuTimeZoneCatalog
import io.github.rhjihan.slackwake.sound.AlarmSoundFactory
import io.github.rhjihan.slackwake.sound.AlertSoundSource
import io.github.rhjihan.slackwake.vibration.AlertVibrationSource
import io.github.rhjihan.slackwake.vibration.DeviceVibrator

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun alertSoundSource(impl: AlarmSoundFactory): AlertSoundSource

    @Binds
    abstract fun alertVibrationSource(impl: DeviceVibrator): AlertVibrationSource

    @Binds
    abstract fun timeZoneCatalog(impl: IcuTimeZoneCatalog): TimeZoneCatalog
}
