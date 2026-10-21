// SPDX-License-Identifier: GPL-3.0-only
// Sans Keyboard: pick a fixed accent color for specific apps instead of the one taken from their icon.

package helium314.keyboard.settings.preferences

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.AppAccent
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ColorPickerDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog

private data class LaunchableApp(val packageName: String, val label: String)

@Composable
fun AppAccentOverridesPreference(setting: Setting) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var pickedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var overrides by remember { mutableStateOf(AppAccent.manualOverrides(prefs)) }

    Preference(
        name = setting.title,
        description = setting.description,
        onClick = { showAppPicker = true },
    )

    if (showAppPicker) {
        val apps = remember {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != ctx.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
        val marker = stringResource(R.string.sans_custom_color_marker)
        ListPickerDialog(
            onDismissRequest = { showAppPicker = false },
            items = apps,
            onItemSelected = { pickedPackage = it.packageName; showAppPicker = false },
            title = { Text(stringResource(R.string.sans_pick_app)) },
            getItemName = { if (it.packageName in overrides) "${it.label}  ●  $marker" else it.label },
            showRadioButtons = false,
        )
    }

    val pkg = pickedPackage
    if (pkg != null) {
        val label = remember(pkg) {
            try {
                ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
        }
        val initial = remember(pkg) { AppAccent.accentFor(ctx, prefs, pkg) ?: KeyboardTheme.SANS_DEFAULT_ACCENT }
        ColorPickerDialog(
            onDismissRequest = { pickedPackage = null },
            initialColor = initial,
            title = label,
            showDefault = true, // "default" goes back to the automatic color from the app icon
            onDefault = {
                AppAccent.setManualOverride(prefs, pkg, null)
                overrides = AppAccent.manualOverrides(prefs)
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            },
            onConfirmed = { color ->
                AppAccent.setManualOverride(prefs, pkg, color)
                overrides = AppAccent.manualOverrides(prefs)
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            },
        )
    }
}
