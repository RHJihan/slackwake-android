// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.core.color.ColorUtil
import io.github.rhjihan.slackwake.core.color.Rgb
import kotlin.math.roundToInt

/** Same quick picks as ColorPickerWindow: the colors that actually read as an alert flash. */
val FLASH_PRESETS =
    listOf(
        "#000000",
        "#FFFFFF",
        "#FF0000",
        "#FF7A00",
        "#FFD400",
        "#3ACC90",
        "#00C853",
        "#00B8D4",
        "#2979FF",
        "#3D5AFE",
        "#7C4DFF",
        "#D500F9",
        "#FF1744",
        "#FF4081",
        "#795548",
        "#9E9E9E",
    )

private fun Rgb.toColor() = Color(toArgb())

/**
 * Compose port of the Fluent ColorPickerWindow. Works in HSV (plane = saturation × value,
 * rail = hue); hex and RGB fields echo the same color. A half-typed hex leaves the color
 * untouched (ColorUtil.tryParseHex returns null instead of black).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val initial = remember { ColorUtil.toHsv(ColorUtil.parse(initialHex)) }
    var hue by remember { mutableDoubleStateOf(initial.hue) }
    var sat by remember { mutableDoubleStateOf(initial.saturation) }
    var value by remember { mutableDoubleStateOf(initial.value) }
    val rgb = ColorUtil.fromHsv(hue, sat, value)
    var hexText by remember { mutableStateOf(ColorUtil.toHex(rgb)) }
    val focus = LocalFocusManager.current

    fun adopt(c: Rgb) {
        val hsv = ColorUtil.toHsv(c)
        // Keep the hue rail where it is for greys (hue is undefined at zero saturation).
        if (hsv.saturation > 0) hue = hsv.hue
        sat = hsv.saturation
        value = hsv.value
        hexText = ColorUtil.toHex(c)
    }

    fun fromHsv() {
        hexText = ColorUtil.toHex(ColorUtil.fromHsv(hue, sat, value))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        confirmButton = { TextButton(onClick = { onSelect(ColorUtil.toHex(rgb)) }) { Text(stringResource(R.string.select)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = {
            Column(
                Modifier
                    .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val planeLabel = stringResource(R.string.color_plane_a11y)
                    val hueColor = ColorUtil.fromHsv(hue, 1.0, 1.0).toColor()
                    // Saturation/value plane.
                    Canvas(
                        Modifier
                            .weight(1f)
                            .height(180.dp)
                            .semantics {
                                contentDescription = planeLabel
                                stateDescription = ColorUtil.toHex(rgb)
                            }.pointerInput(Unit) {
                                fun set(o: Offset) {
                                    sat = (o.x / size.width).toDouble().coerceIn(0.0, 1.0)
                                    value = (1 - o.y / size.height).toDouble().coerceIn(0.0, 1.0)
                                    fromHsv()
                                }
                                detectTapGestures { set(it) }
                            }.pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    sat = (change.position.x / size.width).toDouble().coerceIn(0.0, 1.0)
                                    value = (1 - change.position.y / size.height).toDouble().coerceIn(0.0, 1.0)
                                    fromHsv()
                                }
                            },
                    ) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        val thumb = Offset((sat * size.width).toFloat(), ((1 - value) * size.height).toFloat())
                        drawCircle(Color.White, radius = 9.dp.toPx(), center = thumb, style = Stroke(3.dp.toPx()))
                        drawCircle(Color.Black, radius = 11.dp.toPx(), center = thumb, style = Stroke(1.dp.toPx()))
                    }
                    // Hue rail.
                    val hueLabel = stringResource(R.string.color_hue_a11y)
                    Canvas(
                        Modifier
                            .width(28.dp)
                            .height(180.dp)
                            .semantics {
                                contentDescription = hueLabel
                                stateDescription = "${hue.roundToInt()}°"
                            }.pointerInput(Unit) {
                                detectTapGestures {
                                    hue = (it.y / size.height * 360).toDouble().coerceIn(0.0, 359.999)
                                    fromHsv()
                                }
                            }.pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    hue = (change.position.y / size.height * 360).toDouble().coerceIn(0.0, 359.999)
                                    fromHsv()
                                }
                            },
                    ) {
                        drawRect(Brush.verticalGradient((0..6).map { ColorUtil.fromHsv(it * 60.0, 1.0, 1.0).toColor() }))
                        val y = (hue / 360 * size.height).toFloat()
                        drawRect(
                            Color.White,
                            topLeft = Offset(0f, y - 2.dp.toPx()),
                            size =
                                androidx.compose.ui.geometry
                                    .Size(size.width, 4.dp.toPx()),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(rgb.toColor(), RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { t ->
                            hexText = t
                            ColorUtil.tryParseHex(t)?.let { c ->
                                val hsv = ColorUtil.toHsv(c)
                                if (hsv.saturation > 0) hue = hsv.hue
                                sat = hsv.saturation
                                value = hsv.value
                            }
                        },
                        label = { Text(stringResource(R.string.color_hex)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelField(stringResource(R.string.color_red), rgb.r, Modifier.weight(1f)) { adopt(rgb.copy(r = it)) }
                    ChannelField(stringResource(R.string.color_green), rgb.g, Modifier.weight(1f)) { adopt(rgb.copy(g = it)) }
                    ChannelField(stringResource(R.string.color_blue), rgb.b, Modifier.weight(1f)) { adopt(rgb.copy(b = it)) }
                }

                Text(stringResource(R.string.color_palette), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FLASH_PRESETS.forEach { hex ->
                        val label = stringResource(R.string.color_preset, hex)
                        Box(
                            Modifier
                                .size(48.dp)
                                .clickable(onClickLabel = label) { adopt(ColorUtil.parse(hex)) }
                                .semantics { contentDescription = label }
                                .padding(6.dp)
                                .background(ColorUtil.parse(hex).toColor(), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ChannelField(
    label: String,
    channel: Int,
    modifier: Modifier,
    onChange: (Int) -> Unit,
) {
    var text by remember(channel) { mutableStateOf(channel.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            if (t.length <= 3 && t.all(Char::isDigit)) {
                text = t
                t.toIntOrNull()?.takeIf { it in 0..255 }?.let(onChange)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth().padding(0.dp),
    )
}
