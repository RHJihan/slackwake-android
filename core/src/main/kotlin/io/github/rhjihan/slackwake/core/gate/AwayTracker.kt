// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.gate

/** Monotonic time source (SystemClock.elapsedRealtime on device; a fake in tests). */
fun interface Clock {
    fun nowMs(): Long
}

/** Device facts sampled at evaluation time (KeyguardManager / PowerManager on device). */
data class DeviceState(
    val keyguardLocked: Boolean,
    val interactive: Boolean,
)

/**
 * Android replacement for the Windows GetLastInputInfo idle timer.
 *
 * "Away" means: the device has been locked continuously for at least the idle timeout,
 * measured from the last screen-off that was not followed by an unlock. Driven by
 * broadcasts (ACTION_SCREEN_OFF, ACTION_SCREEN_ON, ACTION_USER_PRESENT) and evaluated
 * lazily when a notification arrives — there is no polling.
 *
 *  - A notification lighting up the screen does NOT end the away period; only an unlock
 *    (USER_PRESENT) does. Screen-on only counts as presence when the keyguard is not
 *    locked at that moment (no lock screen, Smart Lock, or already unlocked).
 *  - Devices with no lock screen: the keyguard is never "locked", so away falls back to
 *    "the screen has been off for at least the timeout".
 *  - Between screen-off and the keyguard engaging (the lock grace period) the screen-off
 *    fallback applies too, so the timeout is always measured from screen-off.
 *
 * Not thread-safe: call from one thread (the listener service's main thread).
 */
class AwayTracker(
    private val clock: Clock,
) {
    /** Start of the current away period, or null while the user is present. */
    var awaySinceMs: Long? = null
        private set

    /**
     * Seed from the device state when the listener connects. If we cannot know when
     * the device locked, start counting now — conservative: never arms early.
     */
    fun initialize(state: DeviceState) {
        awaySinceMs = if (state.interactive && !state.keyguardLocked) null else clock.nowMs()
    }

    fun onScreenOff() {
        if (awaySinceMs == null) awaySinceMs = clock.nowMs()
    }

    /** Screen turned on. Only presence if the keyguard is not in the way. */
    fun onScreenOn(keyguardLocked: Boolean) {
        if (!keyguardLocked) awaySinceMs = null
    }

    /** The user unlocked the device. */
    fun onUserPresent() {
        awaySinceMs = null
    }

    /** Milliseconds the user has been away, or 0 when present. */
    fun awayForMs(state: DeviceState): Long {
        val since = awaySinceMs ?: return 0
        // Screen on and unlocked but we missed the broadcast (e.g. receiver registered
        // late): the user is evidently here.
        if (state.interactive && !state.keyguardLocked) return 0
        return (clock.nowMs() - since).coerceAtLeast(0)
    }

    fun isAway(
        state: DeviceState,
        idleTimeoutSeconds: Int,
    ): Boolean = awaySinceMs != null && awayForMs(state) >= idleTimeoutSeconds * 1000L
}
