package fr.manu.tache

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/** Le style de Métro Dodo : écritures Cormorant Garamond / Source Sans 3 et or dégradé. */
object Look {
    val GOLD = intArrayOf(
        0xFFF7E3A1.toInt(), 0xFFD4A94E.toInt(), 0xFFA87B2C.toInt(), 0xFFECD08A.toInt()
    )
    private val POS = floatArrayOf(0f, 0.45f, 0.72f, 1f)

    private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
        try { ResourcesCompat.getFont(context, id) ?: fallback } catch (e: Exception) { fallback }

    fun sans(context: Context): Typeface = font(context, R.font.tache_sans, Typeface.DEFAULT)
    fun sansBold(context: Context): Typeface = Typeface.create(sans(context), Typeface.BOLD)
    fun serif(context: Context): Typeface = font(context, R.font.tache_serif, Typeface.SERIF)
    fun serifBold(context: Context): Typeface = Typeface.create(serif(context), Typeface.BOLD)

    /** Fond or dégradé (boutons, onglet choisi). */
    fun goldFill(radiusPx: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, GOLD).apply {
            if (Build.VERSION.SDK_INT >= 29) setColors(GOLD, POS)
            cornerRadius = radiusPx
        }

    /** Texte en or dégradé (titres). */
    fun goldText(tv: TextView) {
        tv.setTextColor(GOLD[1])
        tv.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            val w = (r - l).toFloat()
            val h = (b - t).toFloat()
            if (w > 0f && h > 0f) {
                (v as TextView).paint.shader =
                    LinearGradient(0f, 0f, w, h, GOLD, POS, Shader.TileMode.CLAMP)
                v.invalidate()
            }
        }
    }
}
