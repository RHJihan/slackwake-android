// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Status colors from the Windows status dot (traffic-light / presence convention). */
object StatusColors {
    val Armed = Color(0xFF2EB67D)
    val Paused = Color(0xFFE3B341)
    val Disabled = Color(0xFFE5484D)
}

private val BrandGreen = Color(0xFF1E9E6A)

private val LightFallback = lightColorScheme(primary = BrandGreen, secondary = Color(0xFF3ACC90))
private val DarkFallback = darkColorScheme(primary = Color(0xFF3ACC90), secondary = BrandGreen)

/** Material 3, dynamic color on 12+, light/dark following the system. */
@Composable
fun SlackWakeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val ctx = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            }

            darkTheme -> {
                DarkFallback
            }

            else -> {
                LightFallback
            }
        }
    MaterialTheme(colorScheme = scheme, content = content)
}
