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
    /** smoky gray keys on a frosted panel with rounded corners and toolbar buttons in circles */
    const val MODE_SMOKY = "smoky"
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

    fun wantsBlur(context: Context, prefs: SharedPreferences) =
        (mode(prefs) == MODE_FROSTED || mode(prefs) == MODE_SMOKY) && blurAvailable(context)

    /** frosted modes that blur something: Android's window blur, or else Samsung's own blur */
    fun wantsAnyBlur(context: Context, prefs: SharedPreferences) =
        (mode(prefs) == MODE_FROSTED || mode(prefs) == MODE_SMOKY) && (blurAvailable(context) || SamsungBlur.available)

    /** short status for the settings screen, so a missing blur can be explained */
    fun status(context: Context): String {
        val pt = context.resources.configuration.locales[0].language == "pt"
        return when {
            blurAvailable(context) -> if (pt) "Desfoque: ativo (Android)" else "Blur: active (Android)"
            SamsungBlur.available -> (if (pt) "Desfoque do Android desligado neste aparelho. Samsung: " else "Android blur is off on this device. Samsung: ") +
                (SamsungBlur.lastResult ?: if (pt) "ainda não testado (abra o teclado uma vez)" else "not tried yet (open the keyboard once)") +
                (if (pt) ". Para o desfoque real, desligue Acessibilidade > Melhorias de visibilidade > \"Reduzir transparência e desfoque\" e a economia de energia."
                 else ". For real blur, turn off Accessibility > Visibility enhancements > \"Reduce transparency and blur\" and power saving.")
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> if (pt) "Desfoque: indisponível (precisa de Android 12+)" else "Blur: unavailable (needs Android 12+)"
            else -> if (pt) "Desfoque: desligado pelo sistema. Verifique a economia de energia e, nas Opções do desenvolvedor, \"Permitir desfoque no nível da janela\""
                else "Blur: turned off by the system. Check power saving and, in Developer options, \"Allow window-level blurs\""
        }
    }

    fun panelRadius(context: Context) = 28f * context.resources.displayMetrics.density

    @JvmStatic
    fun apply(context: Context, prefs: SharedPreferences, base: Colors): Colors {
        if (!isEnabled(prefs)) return base
        // the frosted look only needs a light veil; without blur the app behind must be toned down more
        val blur = wantsAnyBlur(context, prefs)
        val realBlur = wantsBlur(context, prefs) // Android's blur is known to work; Samsung's may be ignored
        if (mode(prefs) == MODE_SMOKY) {
            val veil = if (realBlur) 0x8C141416.toInt() else 0xC2141416.toInt()
            return GlassColors(base, veil, smoky = true, panelRadius = panelRadius(context), density = context.resources.displayMetrics.density,
                samsungBlur = blur && !blurAvailable(context), grain = !realBlur)
        }
        val veil = if (realBlur) 0x47000000 else 0xA6101012.toInt()
        return GlassColors(base, veil, panelRadius = 0f, density = context.resources.displayMetrics.density,
            samsungBlur = blur && !blurAvailable(context), grain = !realBlur)
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
        // a rounded (transparent) window background also rounds the corners of the blur
        window.setBackgroundDrawable(
            if (mode(prefs) == MODE_SMOKY) android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.TRANSPARENT); cornerRadius = panelRadius(ims)
            } else ColorDrawable(Color.TRANSPARENT)
        )
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
class GlassColors(
    private val base: Colors,
    @ColorInt private val veil: Int,
    private val smoky: Boolean = false,
    private val panelRadius: Float = 0f,
    private val density: Float = 1f,
    /** Android's window blur is off: blur the keyboard frame with Samsung's blur instead */
    private val samsungBlur: Boolean = false,
    /** without a working blur, a fine grain makes the veil read as frosted glass instead of tinted plastic */
    private val grain: Boolean = false,
) : Colors {
    override val themeStyle: String get() = base.themeStyle
    override val hasKeyBorders: Boolean get() = true

    private val stripStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
        intArrayOf(0x2EFFFFFF, Color.TRANSPARENT)
    )

    override fun haveColorsChanged(context: Context) = base.haveColorsChanged(context)

    override fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable = when (color) {
        KEY_BACKGROUND, FUNCTIONAL_KEY_BACKGROUND, SPACE_BAR_BACKGROUND ->
            if (smoky) SmokyKeyDrawable(base.get(ACTION_KEY_BACKGROUND)) else GlassKeyDrawable(null, base.get(ACTION_KEY_BACKGROUND))
        // smoky: the action key looks like the others, only its icon takes the accent color
        ACTION_KEY_BACKGROUND -> if (smoky) SmokyKeyDrawable(base.get(ACTION_KEY_BACKGROUND))
            else GlassKeyDrawable(base.get(ACTION_KEY_BACKGROUND), base.get(ACTION_KEY_BACKGROUND))
        else -> base.selectAndColorDrawable(attr, color) // popups stay solid so they are easy to read
    }

    override fun setColor(drawable: Drawable, color: ColorType) {
        if (smoky && color == ACTION_KEY_ICON) { drawable.colorFilter = accentIconFilter; return }
        if (color == STRIP_BACKGROUND) {
            DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
            DrawableCompat.setTintList(drawable, stripStateList)
        } else base.setColor(drawable, color)
    }

    override fun get(color: ColorType): Int = when (color) {
        MAIN_BACKGROUND, NAVIGATION_BAR -> veil
        STRIP_BACKGROUND -> Color.TRANSPARENT
        ACTION_KEY_ICON -> if (smoky) base.get(ACTION_KEY_BACKGROUND) else base.get(color)
        else -> base.get(color)
    }

    override fun setColor(view: ImageView, color: ColorType) {
        if (smoky && color == ACTION_KEY_ICON) view.colorFilter = accentIconFilter
        else base.setColor(view, color)
    }

    private val accentIconFilter: ColorFilter = android.graphics.PorterDuffColorFilter(base.get(ACTION_KEY_BACKGROUND), PorterDuff.Mode.SRC_IN)

    override fun setBackground(view: View, color: ColorType) {
        when (color) {
            MAIN_BACKGROUND -> {
                view.background = FrostedPanelDrawable(veil, if (smoky) panelRadius else 0f,
                    if (smoky) 0x50E6E6EB else 0, density, grain)
                if (samsungBlur) SamsungBlur.apply(view, (28 * density).toInt(), if (smoky) panelRadius else 0f)
            }
            STRIP_BACKGROUND -> if (smoky && view is ImageView) {
                view.background = ToolbarCircleDrawable(base.get(ACTION_KEY_BACKGROUND), density)
            } else {
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

/**
 * Smoky key: dark gray, a bit lighter at the top, thin light edge. Pressed keys glow in the accent color.
 */
class SmokyKeyDrawable(@ColorInt private val accent: Int) : Drawable() {
    private val density = android.content.res.Resources.getSystem().displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * density }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = accent
        maskFilter = android.graphics.BlurMaskFilter(3.5f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
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
        if (shaderHeight != b.height() || shaderPressed != pressed) updateShaders(b.top.toFloat(), b.height().toFloat())
        // the inset leaves room for the glow inside the key's own area, so neighbours never cover it
        val inset = 3.5f * density
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        val r = minOf(rect.width(), rect.height()) * 0.28f
        if (pressed) canvas.drawRoundRect(rect, r, r, glow)
        canvas.drawRoundRect(rect, r, r, fill)
        val half = rim.strokeWidth / 2f
        rect.inset(half, half)
        canvas.drawRoundRect(rect, r - half, r - half, rim)
    }

    private fun updateShaders(top: Float, h: Float) {
        shaderHeight = h.toInt()
        shaderPressed = pressed
        val bottom = top + h
        fill.shader = if (pressed) {
            LinearGradient(0f, top, 0f, bottom,
                ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accent, Color.WHITE, 0.2f), 0xE6),
                ColorUtils.setAlphaComponent(accent, 0xCC), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, top, 0f, bottom, 0xB8434346.toInt(), 0xB81E1E20.toInt(), Shader.TileMode.CLAMP)
        }
        rim.shader = if (pressed) {
            LinearGradient(0f, top, 0f, bottom, ColorUtils.blendARGB(accent, Color.WHITE, 0.6f), accent, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, top, 0f, bottom, 0x5AFFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP)
        }
    }

    override fun getIntrinsicWidth() = (56 * density).toInt()
    override fun getIntrinsicHeight() = (44 * density).toInt()
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** round smoky button behind toolbar icons, lights up in the accent color when pressed or active */
class ToolbarCircleDrawable(@ColorInt private val accent: Int, private val density: Float) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * density }
    private var highlighted = false

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val h = state.contains(android.R.attr.state_pressed) || state.contains(android.R.attr.state_activated)
        if (h == highlighted) return false
        highlighted = h
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val radius = minOf(b.width(), b.height()) / 2f - 4f * density
        if (radius <= 0) return
        fill.color = if (highlighted) ColorUtils.setAlphaComponent(accent, 0xCC) else 0xB028282A.toInt()
        ring.color = if (highlighted) accent else 0x46FFFFFF
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, fill)
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius - ring.strokeWidth / 2f, ring)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Samsung's own real-time blur (One UI), used when Android's cross-window blur is disabled on the device.
 * The API is undocumented, so every member is looked up by reflection and any failure just leaves no blur.
 */
object SamsungBlur {
    private val infoClass: Class<*>? = try { Class.forName("android.view.SemBlurInfo") } catch (_: Throwable) { null }
    private val builderClass: Class<*>? = try { Class.forName("android.view.SemBlurInfo\$Builder") } catch (_: Throwable) { null }
    private val setBlurInfo: java.lang.reflect.Method? = try {
        if (infoClass == null) null else View::class.java.getMethod("semSetBlurInfo", infoClass)
    } catch (_: Throwable) { null }

    val available: Boolean get() = infoClass != null && builderClass != null && setBlurInfo != null

    /** what happened the last time the blur was applied, shown in the settings */
    @Volatile var lastResult: String? = null

    private fun windowMode(): Int = try {
        infoClass!!.getField("BLUR_MODE_WINDOW").getInt(null)
    } catch (_: Throwable) { 0 }

    private fun call(target: Any, names: List<String>, arg: Any, type: Class<*>): Boolean {
        for (n in names) {
            try { builderClass!!.getMethod(n, type).invoke(target, arg); return true } catch (_: Throwable) { }
        }
        return false
    }

    /** returns true if the blur info was accepted by the view */
    fun apply(view: View, radius: Int, cornerRadius: Float): Boolean {
        if (!available) return false
        return try {
            val mode = windowMode()
            val builder = try {
                builderClass!!.getConstructor(Int::class.javaPrimitiveType).newInstance(mode)
            } catch (_: Throwable) {
                builderClass!!.getConstructor().newInstance().also { call(it, listOf("setBlurMode"), mode, Int::class.javaPrimitiveType!!) }
            }
            call(builder, listOf("setRadius", "setBlurRadius"), radius, Int::class.javaPrimitiveType!!)
            call(builder, listOf("setBackgroundColor"), 0x26000000, Int::class.javaPrimitiveType!!)
            if (cornerRadius > 0f) call(builder, listOf("setBackgroundCornerRadius"), cornerRadius, Float::class.javaPrimitiveType!!)
            val info = builderClass!!.getMethod("build").invoke(builder)
            // apply after layout, as One UI does
            view.post {
                lastResult = try {
                    setBlurInfo!!.invoke(view, info)
                    "aceito pelo sistema (se o fundo não ficou fosco, a One UI ignora o pedido para apps de terceiros)"
                } catch (e: Throwable) {
                    val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
                    "recusado (" + cause.javaClass.simpleName + (cause.message?.let { ": " + it.take(80) } ?: "") + ")"
                }
            }
            true
        } catch (e: Throwable) {
            lastResult = "recusado (" + e.javaClass.simpleName + ")"
            false
        }
    }
}

/** keyboard background: translucent veil, optional rim and a fine static grain that reads as frosted glass */
class FrostedPanelDrawable(
    @ColorInt private val veil: Int,
    private val radius: Float,
    @ColorInt private val rimColor: Int,
    private val density: Float,
    private val grain: Boolean,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = veil }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * density; color = rimColor }
    private val grainPaint = Paint().apply { if (grain) shader = android.graphics.BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        canvas.drawRoundRect(rect, radius, radius, fill)
        if (grain) canvas.drawRoundRect(rect, radius, radius, grainPaint)
        // a soft light sheen at the top, like light catching a glass pane
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, rect.top, 0f, rect.top + 60 * density, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, radius, radius, sheen)
        if (Color.alpha(rimColor) > 0) {
            val h = rim.strokeWidth / 2f
            rect.inset(h, h)
            canvas.drawRoundRect(rect, radius - h, radius - h, rim)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** 96x96 tile of soft white/black specks, generated once */
        private val noise: android.graphics.Bitmap by lazy {
            val size = 96
            val rnd = java.util.Random(7)
            val px = IntArray(size * size) {
                val v = rnd.nextGaussian()
                when {
                    v > 1.2 -> Color.argb((18 + rnd.nextInt(14)), 255, 255, 255)
                    v < -1.2 -> Color.argb((16 + rnd.nextInt(12)), 0, 0, 0)
                    else -> Color.argb(6, 255, 255, 255)
                }
            }
            android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888)
        }
    }
}
