// SPDX-License-Identifier: GPL-3.0-only
// Sans Keyboard: per-app accent color, picked from the icon of the app that is currently using the keyboard.

package helium314.keyboard.latin.common

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType.*
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.brightenOrDarken

object AppAccent {
    private const val TAG = "AppAccent"
    private const val ICON_SIZE = 48
    private const val NO_COLOR = 0

    /** package of the app the keyboard is currently typing into, set by LatinIME when settings are loaded */
    @Volatile
    @JvmStatic
    var currentPackage: String? = null

    /** accent in use by the most recently created colors, 0 if none; KeyboardSwitcher reloads the views when it changes */
    @Volatile
    @JvmStatic
    var currentAccent: Int = NO_COLOR
        private set

    // extracted colors per package (NO_COLOR means the icon has no usable color)
    private val cache = LruCache<String, Int>(100)

    @JvmStatic
    fun clearCache() = cache.evictAll()

    /** wraps [base] so accent-colored parts follow the current app, if enabled and a color is found */
    @JvmStatic
    fun apply(context: Context, prefs: SharedPreferences, base: Colors, isNight: Boolean): Colors {
        if (!prefs.getBoolean(Settings.PREF_SANS_ADAPTIVE_ACCENT, Defaults.PREF_SANS_ADAPTIVE_ACCENT)
                || base.themeStyle == KeyboardTheme.STYLE_HOLO) {
            currentAccent = NO_COLOR
            return base
        }
        val accent = accentFor(context, prefs, currentPackage)
        if (accent == null) {
            currentAccent = NO_COLOR
            return base
        }
        val adjusted = adjustForTheme(accent, isNight)
        currentAccent = adjusted
        return AccentOverrideColors(base, adjusted)
    }

    @ColorInt
    fun accentFor(context: Context, prefs: SharedPreferences, packageName: String?): Int? {
        if (packageName.isNullOrEmpty() || packageName == context.packageName) return null
        manualOverride(prefs, packageName)?.let { return it }
        val cached = cache.get(packageName)
        if (cached != null) return cached.takeIf { it != NO_COLOR }
        val extracted = try {
            val icon = context.packageManager.getApplicationIcon(packageName)
            dominantColor(icon)
        } catch (e: Exception) {
            Log.w(TAG, "could not read icon of $packageName", e)
            null
        }
        cache.put(packageName, extracted ?: NO_COLOR)
        return extracted
    }

    /** manual colors are stored as "package:#RRGGBB;package2:#RRGGBB" */
    fun manualOverrides(prefs: SharedPreferences): Map<String, Int> {
        val raw = prefs.getString(Settings.PREF_SANS_APP_ACCENT_OVERRIDES, Defaults.PREF_SANS_APP_ACCENT_OVERRIDES) ?: return emptyMap()
        return raw.split(";").mapNotNull { entry ->
            val pkg = entry.substringBefore(":", "").trim()
            val color = entry.substringAfter(":", "").trim()
            if (pkg.isEmpty() || color.isEmpty()) return@mapNotNull null
            try { pkg to Color.parseColor(color) } catch (_: IllegalArgumentException) { null }
        }.toMap()
    }

    fun setManualOverride(prefs: SharedPreferences, packageName: String, @ColorInt color: Int?) {
        val map = manualOverrides(prefs).toMutableMap()
        if (color == null) map.remove(packageName) else map[packageName] = color
        val raw = map.entries.joinToString(";") { "${it.key}:#%06X".format(it.value and 0xFFFFFF) }
        prefs.edit().putString(Settings.PREF_SANS_APP_ACCENT_OVERRIDES, raw).apply()
    }

    private fun manualOverride(prefs: SharedPreferences, packageName: String): Int? = manualOverrides(prefs)[packageName]

    /** most prominent saturated color of the icon, or null for monochrome icons */
    @ColorInt
    fun dominantColor(icon: Drawable): Int? {
        val bitmap = icon.toBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(ICON_SIZE * ICON_SIZE)
        bitmap.getPixels(pixels, 0, ICON_SIZE, 0, 0, ICON_SIZE, ICON_SIZE)

        val buckets = 36 // 10° hue steps
        val weight = FloatArray(buckets)
        val r = FloatArray(buckets)
        val g = FloatArray(buckets)
        val b = FloatArray(buckets)
        val hsv = FloatArray(3)
        var opaque = 0
        for (p in pixels) {
            if (Color.alpha(p) < 200) continue
            opaque++
            Color.colorToHSV(p, hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (s < 0.25f || v < 0.22f) continue // grays, whites, blacks
            val w = s * v
            val i = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            weight[i] += w
            r[i] += Color.red(p) * w
            g[i] += Color.green(p) * w
            b[i] += Color.blue(p) * w
        }
        if (opaque == 0) return null
        // merge each bucket with its neighbours so a hue split across two buckets still wins
        var best = -1
        var bestWeight = 0f
        for (i in 0 until buckets) {
            val w = weight[i] + 0.5f * (weight[(i + 1) % buckets] + weight[(i + buckets - 1) % buckets])
            if (w > bestWeight) { bestWeight = w; best = i }
        }
        if (best < 0 || weight[best] < opaque * 0.04f) return null
        val w = weight[best]
        return Color.rgb((r[best] / w).toInt(), (g[best] / w).toInt(), (b[best] / w).toInt())
    }

    /** keeps the accent readable on the keyboard: not too dark at night, not too pale by day */
    @ColorInt
    fun adjustForTheme(@ColorInt color: Int, isNight: Boolean): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.35f)
        hsl[2] = if (isNight) hsl[2].coerceIn(0.42f, 0.62f) else hsl[2].coerceIn(0.36f, 0.56f)
        return ColorUtils.HSLToColor(hsl)
    }
}

/** delegates to [base], but uses [accent] for the action key, pressed keys and other accent-colored parts */
class AccentOverrideColors(private val base: Colors, @ColorInt private val accent: Int) : Colors {
    override val themeStyle: String get() = base.themeStyle
    override val hasKeyBorders: Boolean get() = base.hasKeyBorders

    private val accentFilter: ColorFilter = filter(accent)
    private val actionKeyStateList = pressedStateList(brightenOrDarken(accent, true), accent)
    private val pressedKeyStateLists = HashMap<ColorType, android.content.res.ColorStateList>()

    override fun haveColorsChanged(context: Context) = base.haveColorsChanged(context)

    override fun get(color: ColorType): Int = when (color) {
        ACTION_KEY_BACKGROUND, ACTION_KEY_POPUP_KEYS_BACKGROUND, TOOL_BAR_KEY_ENABLED_BACKGROUND,
            EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN, GESTURE_TRAIL -> accent
        SHIFT_KEY_ICON -> if (accentShiftedIcon) accent else base.get(color)
        else -> base.get(color)
    }

    override fun setColor(drawable: Drawable, color: ColorType) {
        when (color) {
            ACTION_KEY_BACKGROUND, ACTION_KEY_POPUP_KEYS_BACKGROUND -> tint(drawable, actionKeyStateList)
            KEY_BACKGROUND, FUNCTIONAL_KEY_BACKGROUND, SPACE_BAR_BACKGROUND -> tint(drawable, pressedKeyStateList(color))
            EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN -> drawable.colorFilter = accentFilter
            SHIFT_KEY_ICON -> if (accentShiftedIcon) drawable.colorFilter = accentFilter else base.setColor(drawable, color)
            else -> base.setColor(drawable, color)
        }
    }

    override fun setColor(view: ImageView, color: ColorType) {
        when (color) {
            EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN -> view.colorFilter = accentFilter
            SHIFT_KEY_ICON -> if (accentShiftedIcon) view.colorFilter = accentFilter else base.setColor(view, color)
            else -> base.setColor(view, color)
        }
    }

    override fun setBackground(view: View, color: ColorType) {
        when (color) {
            // these go through setColor(drawable) in the base classes, route them through ours instead
            KEY_BACKGROUND, FUNCTIONAL_KEY_BACKGROUND, SPACE_BAR_BACKGROUND -> {
                if (view.background == null) view.setBackgroundColor(Color.WHITE)
                setColor(view.background, color)
            }
            else -> base.setBackground(view, color)
        }
    }

    /** pressed keys light up in the accent color, like on the screenshots this is based on */
    private fun pressedKeyStateList(color: ColorType) = pressedKeyStateLists.getOrPut(color) {
        val normal = if (hasKeyBorders || color == SPACE_BAR_BACKGROUND) base.get(color) else Color.TRANSPARENT
        pressedStateList(accent, normal)
    }

    private fun tint(drawable: Drawable, list: android.content.res.ColorStateList) {
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(drawable, list)
    }

    private fun filter(color: Int): ColorFilter =
        BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, BlendModeCompat.MODULATE)!!

    private fun pressedStateList(pressed: Int, normal: Int): android.content.res.ColorStateList {
        val states = arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_pressed))
        return android.content.res.ColorStateList(states, intArrayOf(pressed, normal))
    }
}
