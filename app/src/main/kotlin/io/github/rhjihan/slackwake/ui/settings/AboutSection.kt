// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.rhjihan.slackwake.BuildConfig
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.ui.theme.StatusColors

/**
 * About: app + version, developer (linking to their website) and license. The link opens in the user's own
 * browser via ACTION_VIEW, so SlackWake itself still has no network access.
 */
@Composable
fun AboutSection(versionName: String = BuildConfig.VERSION_NAME) {
    val context = LocalContext.current
    val url = stringResource(R.string.about_website_url)
    val licenseUrl = stringResource(R.string.about_license_url)
    val noBrowser = stringResource(R.string.about_no_browser)
    val open: (String) -> Unit = { link ->
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, link.toUri())
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
        }
    }
    // Lists inside a card take the card's color rather than painting their own.
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)

    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_app)) },
            supportingContent = { Text(stringResource(R.string.about_version, versionName)) },
            leadingContent = { Icon(painterResource(R.drawable.ic_bell_armed), contentDescription = null, tint = StatusColors.Armed) },
            colors = colors,
        )
        // The developer's name is the link to their website.
        LinkItem(
            label = stringResource(R.string.about_author_label),
            text = stringResource(R.string.about_author),
            icon = Icons.Filled.Person,
            onClickLabel = stringResource(R.string.about_open_website),
            tag = "about_developer",
            colors = colors,
        ) { open(url) }
        LinkItem(
            label = stringResource(R.string.about_license_label),
            text = stringResource(R.string.about_license),
            icon = Icons.Filled.Gavel,
            onClickLabel = stringResource(R.string.about_open_license),
            tag = "about_license",
            colors = colors,
        ) { open(licenseUrl) }
        Text(
            stringResource(R.string.about_body_short),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** A list row that opens an external page (trailing "open in new" signals leaving the app). */
@Composable
private fun LinkItem(
    label: String,
    text: String,
    icon: ImageVector,
    onClickLabel: String,
    tag: String,
    colors: ListItemColors,
    onClick: () -> Unit,
) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(text, color = MaterialTheme.colorScheme.primary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        colors = colors,
        modifier = Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick).testTag(tag),
    )
}
