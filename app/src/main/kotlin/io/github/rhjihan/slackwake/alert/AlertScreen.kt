// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.alert

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.core.color.ColorUtil
import io.github.rhjihan.slackwake.core.color.OverlayPalette
import io.github.rhjihan.slackwake.core.model.SlackEvent
import kotlin.math.PI
import kotlin.math.cos

/** Port of OverlayWindow's SineEase(EaseInOut): decelerate into each color, accelerate out. */
internal fun sineEaseInOut(x: Float): Float = (-(cos(PI * x) - 1) / 2).toFloat()

/**
 * Position in the A→B→A cycle for a given elapsed time: a triangle wave with period
 * 2×[halfCycleMs], eased with [sineEaseInOut] (WPF ColorAnimation AutoReverse + SineEase).
 */
internal fun flashFraction(
    elapsedMs: Long,
    halfCycleMs: Int,
): Float {
    val t = (elapsedMs % (2L * halfCycleMs)).toFloat() / halfCycleMs
    val linear = if (t <= 1f) t else 2f - t
    return sineEaseInOut(linear)
}

private fun lerpPalette(
    a: OverlayPalette,
    b: OverlayPalette,
    f: Float,
) = Palette(
    lerp(Color(a.background), Color(b.background), f),
    lerp(Color(a.foreground), Color(b.foreground), f),
    lerp(Color(a.messageBox), Color(b.messageBox), f),
    lerp(Color(a.hint), Color(b.hint), f),
)

private fun OverlayPalette.toPalette() = Palette(Color(background), Color(foreground), Color(messageBox), Color(hint))

private data class Palette(
    val background: Color,
    val foreground: Color,
    val messageBox: Color,
    val hint: Color,
)

/**
 * Full-bleed alert content. [flashStartMs] is the shared monotonic start of the flash so
 * the primary display and every presentation display strobe in phase; null = not
 * flashing (static default look, also used after the auto-stop cap).
 */
@Composable
fun AlertScreen(
    event: SlackEvent,
    colorA: String,
    colorB: String,
    halfCycleMs: Int,
    flashStartMs: Long?,
    nowMs: () -> Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paletteA = remember(colorA) { OverlayPalette.from(ColorUtil.parse(colorA)) }
    val paletteB = remember(colorB) { OverlayPalette.from(ColorUtil.parse(colorB)) }

    var frameNow by remember { mutableLongStateOf(nowMs()) }
    if (flashStartMs != null) {
        LaunchedEffect(flashStartMs) {
            while (true) withFrameMillis { frameNow = nowMs() }
        }
    }
    val palette =
        if (flashStartMs == null) {
            OverlayPalette.DEFAULT.toPalette()
        } else {
            lerpPalette(paletteA, paletteB, flashFraction(frameNow - flashStartMs, halfCycleMs))
        }

    // Entrance: fade in + scale 0.96 → 1, like the WPF storyboard.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(durationMillis = 320, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))) }

    val dismissLabel = stringResource(R.string.alert_dismiss_a11y)
    val description = stringResource(R.string.alert_a11y_description)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
                .semantics {
                    contentDescription = description
                    onClick(label = dismissLabel) {
                        onDismiss()
                        true
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 48.dp)
                    .widthIn(max = 720.dp)
                    .graphicsLayer {
                        alpha = entrance.value
                        val s = 0.96f + 0.04f * entrance.value
                        scaleX = s
                        scaleY = s
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = palette.foreground,
                modifier = Modifier.size(76.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.alert_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = palette.foreground,
                textAlign = TextAlign.Center,
            )
            event.channel?.let {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.alert_channel, it),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.foreground,
                    textAlign = TextAlign.Center,
                )
            }
            event.sender?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.alert_from, it),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.foreground,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .background(palette.messageBox, RoundedCornerShape(16.dp))
                    .border(1.dp, palette.hint, RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Text(
                    event.text ?: stringResource(R.string.alert_no_preview),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.foreground,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier
                    .background(palette.messageBox, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.alert_dismiss_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.hint,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
