package io.github.dhianapereira.afazeres.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dhianapereira.afazeres.R

internal fun settingsTitle(page: String): Int = when (page) {
    "preferences" -> R.string.preferences
    "data" -> R.string.data_and_backup
    "legal" -> R.string.legal
    "about" -> R.string.about
    "archived" -> R.string.archived
    "learning" -> R.string.learning
    "learning_categories" -> R.string.category_examples
    "learning_priorities" -> R.string.priority_examples
    "learning_simulate" -> R.string.simulate_learning
    else -> R.string.settings
}

internal fun settingsParent(page: String) = if (page.startsWith("learning_")) "learning" else "main"

@Composable
internal fun SettingsContent(
    page: String,
    theme: String,
    busy: Boolean,
    onPage: (String) -> Unit,
    onSheet: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (page) {
            "main" -> {
                SettingsRow(Icons.Outlined.Tune, R.string.preferences, stringResource(R.string.preferences_description)) { onPage("preferences") }
                SettingsRow(Icons.Outlined.Folder, R.string.data_and_backup, stringResource(R.string.backup_description)) { onPage("data") }
                SettingsRow(Icons.Outlined.Archive, R.string.archived, stringResource(R.string.archived_description)) { onPage("archived") }
                SettingsRow(Icons.Outlined.Psychology, R.string.learning, stringResource(R.string.learning_description)) { onPage("learning") }
                SettingsRow(Icons.Outlined.Gavel, R.string.legal, stringResource(R.string.legal_description)) { onPage("legal") }
                SettingsRow(Icons.Outlined.Info, R.string.about, stringResource(R.string.about_description)) { onPage("about") }
            }
            "preferences" -> {
                SettingsRow(Icons.Outlined.DarkMode, R.string.theme, stringResource(when (theme) { "light" -> R.string.light; "dark" -> R.string.dark; else -> R.string.system })) { onSheet("theme") }
                SettingsRow(Icons.Outlined.Language, R.string.language, stringResource(R.string.language_current)) { onSheet("language") }
            }
            "data" -> {
                SettingsRow(Icons.Outlined.Upload, R.string.export_data, stringResource(R.string.export_description), !busy, onExport)
                SettingsRow(Icons.Outlined.Download, R.string.import_data, stringResource(R.string.import_description), !busy, onImport)
            }
            "legal" -> {
                // Enable these entries when the external document URLs are available.
                SettingsRow(Icons.Outlined.Description, R.string.privacy_policy, "", enabled = false) {}
                SettingsRow(Icons.Outlined.Description, R.string.terms_of_use, "", enabled = false) {}
            }
            "about" -> {
                Text(stringResource(R.string.about_app), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.developed_by), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Dhiana Pereira", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                val context = LocalContext.current
                val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
                Text(stringResource(R.string.version), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(version, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
