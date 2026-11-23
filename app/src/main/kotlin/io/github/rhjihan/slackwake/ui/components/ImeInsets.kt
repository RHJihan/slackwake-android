// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.roundToInt

/**
 * Pads the bottom by exactly the part of the on-screen keyboard that overlaps this element,
 * following the keyboard's open and close animation.
 *
 * This replaces `adjustResize`, which stops resizing the window once the app is edge-to-edge.
 * Unlike [androidx.compose.foundation.layout.imePadding], it doesn't count whatever already sits
 * between the element and the bottom of the window (a navigation bar, the system bars). The
 * content therefore ends right at the keyboard's top edge, with no extra gap.
 *
 * Put it on the scroll container itself, not in its `contentPadding`. That shrinks the viewport,
 * and the container then scrolls the focused text field (and its cursor) back into view.
 */
fun Modifier.imeOverlapPadding(): Modifier =
    composed {
        // Distance from this element's bottom edge to the window's bottom edge. It depends only
        // on where the element sits, not on the padding below, so it doesn't change as the
        // keyboard animates.
        var spaceBelow by remember { mutableIntStateOf(0) }
        val overlap = WindowInsets.ime.exclude(WindowInsets(bottom = spaceBelow)).asPaddingValues()
        this
            .onPlaced { coords ->
                val windowHeight = coords.findRootCoordinates().size.height
                val bottom = coords.positionInRoot().y + coords.size.height
                spaceBelow = (windowHeight - bottom).roundToInt().coerceAtLeast(0)
            }.padding(overlap)
    }
