# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Md. Rifat Hasan Jihan
#
# AndroidX, Hilt, Compose and kotlinx.serialization ship their own consumer rules.
# Keep the persisted settings model's serializer stable across R8 renames.
-keepclassmembers @kotlinx.serialization.Serializable class io.github.rhjihan.slackwake.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
