// SPDX-License-Identifier: GPL-3.0-only
// Sans Keyboard: "glass" look. Keys are black at the top fading to see-through at the bottom with a thin
// light rim (like glass icon packs), and the keyboard background is the app being typed in, either
// frosted (window background blur) or clear with a dark veil.

package helium314.keyboard.latin.common

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import helium314.keyboard.latin.common.ColorType.*
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings

object Glass {
    const val MODE_FROSTED = "frosted"
    const val MODE_CLEAR = "clear"
    const val MODE_OFF = "off"

    private const val BLUR_RADIUS_DP = 28

    fun mode(prefs: SharedPreferences): String =
        prefs.getString(Settings.PREF_SANS_GLASS, Defaults.PREF_SANS_GLASS) ?: MODE_OFF

    fun isEnabled(prefs: SharedPreferences) = mode(prefs) != MODE_OFF

    /** whether the system can blur what is behind the keyboard right now (off e.g. in power saving mode) */
    fun blurAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        } catch (_: Exception) { false }
    }

    fun wantsBlur(context: Context, prefs: SharedPreferences) = mode(prefs) == MODE_FROSTED && blurAvailable(context)

    @JvmStatic
    fun apply(context: Context, prefs: SharedPreferences, base: Colors): Colors {
        if (!isEnabled(prefs)) return base
        // the frosted look only needs a light veil; without blur the app behind must be toned down more
        val veil = if (wantsBlur(context, prefs)) 0x47000000 else 0x8C000000.toInt()
        return GlassColors(base, veil)
    }

    /**
     * Called whenever the IME window layout is updated. With blur, the window must only cover the keyboard
     * (the blur applies to the whole window), so it is switched to wrap_content. Returns true if the
     * window should wrap its content.
     */
    @JvmStatic
    fun applyToWindow(ims: InputMethodService, prefs: SharedPreferences, floatingOrFullscreen: Boolean): Boolean {
        val window = ims.window?.window ?: return false
        if (!isEnabled(prefs)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setBackgroundBlurRadius(0)
            return false
        }
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val blur = !floatingOrFullscreen && wantsBlur(ims, prefs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val px = if (blur) (BLUR_RADIUS_DP * ims.resources.displayMetrics.density).toInt() else 0
            window.setBackgroundBlurRadius(px)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        return blur
    }
}

/** wraps the theme colors: glass keys, see-through backgrounds, everything else unchanged */
class GlassColors(private val base: Colors, @ColorInt private val veil: Int) : Colors {
    override val themeStyle: String get() = base.themeStyle
    override val hasKeyBorders: Boolean get() = true

    private val stripStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
        intArrayOf(0x2EFFFFFF, Color.TRANSPARENT)
    )

    override fun haveColorsChanged(context: Context) = base.haveColorsChanged(context)

    override fun get(color: ColorType): Int = when (color) {
        MAIN_BACKGROUND, NAVIGATION_BAR -> veil
        STRIP_BACKGROUND -> Color.TRANSPARENT
        else -> base.get(color)
    }

    override fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable = when (color) {
        KEY_BACKGROUND, FUNCTIONAL_KEY_BACKGROUND, SPACE_BAR_BACKGROUND ->
            GlassKeyDrawable(null, base.get(ACTION_KEY_BACKGROUND))
        ACTION_KEY_BACKGROUND -> GlassKeyDrawable(base.get(ACTION_KEY_BACKGROUND), base.get(ACTION_KEY_BACKGROUND))
        else -> base.selectAndColorDrawable(attr, color) // popups stay solid so they are easy to read
    }

    override fun setColor(drawable: Drawable, color: ColorType) {
        if (color == STRIP_BACKGROUND) {
            DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
            DrawableCompat.setTintList(drawable, stripStateList)
        } else base.setColor(drawable, color)
    }

    override fun setColor(view: ImageView, color: ColorType) = base.setColor(view, color)

    override fun setBackground(view: View, color: ColorType) {
        when (color) {
            MAIN_BACKGROUND -> view.background = ColorDrawable(veil)
            STRIP_BACKGROUND -> {
                if (view.background == null) view.setBackgroundColor(Color.WHITE)
                setColor(view.background, color)
            }
            else -> base.setBackground(view, color)
        }
    }
}

/**
 * Key background: rounded key, black at the top fading to almost transparent at the bottom,
 * with a thin light rim that gets brighter towards the bottom. Action keys fade into the accent color.
 * Pressed keys light up in [pressedColor] (the per-app accent).
 */
class GlassKeyDrawable(@ColorInt private val accent: Int?, @ColorInt private val pressedColor: Int) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()
    private var pressed = false
    private var shaderHeight = -1
    private var shaderPressed = false

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val p = state.contains(android.R.attr.state_pressed)
        if (p == pressed) return false
        pressed = p
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val h = b.height().toFloat()
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val stroke = 1.3f * density
        if (shaderHeight != b.height() || shaderPressed != pressed) updateShaders(b.top.toFloat(), h, density)

        // keys get a small inset so neighbouring rims do not touch
        val inset = 1.5f * density
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        val r = minOf(rect.width(), rect.height()) * 0.30f
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.drawPath(path, fill)

        rim.strokeWidth = stroke
        rect.inset(stroke / 2f, stroke / 2f)
        canvas.drawRoundRect(rect, r - stroke / 2f, r - stroke / 2f, rim)
    }

    private fun updateShaders(top: Float, h: Float, density: Float) {
        shaderHeight = h.toInt()
        shaderPressed = pressed
        val bottom = top + h
        val tint = when {
            pressed -> pressedColor
            accent != null -> accent
            else -> null
        }
        fill.shader = if (tint == null) {
            LinearGradient(0f, top, 0f, bottom,
                intArrayOf(0xF7000000.toInt(), 0xC4000000.toInt(), 0x30000000),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, top, 0f, bottom,
                intArrayOf(0xF0000000.toInt(), ColorUtils.setAlphaComponent(tint, 0x80), ColorUtils.setAlphaComponent(tint, 0xE6)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        val rimColor = if (tint == null) 0xFFD2DAE2.toInt() else ColorUtils.blendARGB(tint, Color.WHITE, 0.5f)
        rim.shader = LinearGradient(0f, top, 0f, bottom,
            intArrayOf(ColorUtils.setAlphaComponent(rimColor, 0x14), ColorUtils.setAlphaComponent(rimColor, 0x2A), ColorUtils.setAlphaComponent(rimColor, 0xA0)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }

    // used when a theme keeps the background aspect ratio (action / emoji key without key borders)
    override fun getIntrinsicWidth() = (56 * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    override fun getIntrinsicHeight() = (44 * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
