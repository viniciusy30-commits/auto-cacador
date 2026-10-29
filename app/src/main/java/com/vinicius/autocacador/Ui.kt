package com.vinicius.autocacador

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

const val MP = ViewGroup.LayoutParams.MATCH_PARENT
const val WC = ViewGroup.LayoutParams.WRAP_CONTENT
const val VERT = LinearLayout.VERTICAL
const val HORZ = LinearLayout.HORIZONTAL

object Pal {
    val BG = Color.parseColor("#0E1120")
    val CARD = Color.parseColor("#1A1F36")
    val CARD2 = Color.parseColor("#232949")
    val ACC = Color.parseColor("#7C6CFF")
    val OK = Color.parseColor("#2ED3A5")
    val WARN = Color.parseColor("#FFB347")
    val BAD = Color.parseColor("#FF5C7A")
    val TXT = Color.parseColor("#F2F4FF")
    val SUB = Color.parseColor("#9AA3C7")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(color: Int, radius: Float, strokeColor: Int = 0, strokeW: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radius
    if (strokeW > 0) g.setStroke(strokeW, strokeColor)
    return g
}

fun Context.text(t: String, sp: Float = 14f, color: Int = Pal.TXT, bold: Boolean = false): TextView {
    val v = TextView(this)
    v.text = t
    v.textSize = sp
    v.setTextColor(color)
    if (bold) v.typeface = Typeface.DEFAULT_BOLD
    v.setLineSpacing(0f, 1.15f)
    return v
}

fun Context.button(label: String, color: Int = Pal.ACC, onClick: () -> Unit): TextView {
    val v = TextView(this)
    v.text = label
    v.textSize = 14f
    v.setTextColor(Color.WHITE)
    v.typeface = Typeface.DEFAULT_BOLD
    v.gravity = Gravity.CENTER
    v.setPadding(dp(16), dp(12), dp(16), dp(12))
    v.background = rounded(color, dp(14).toFloat())
    v.setOnClickListener { view ->
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            onClick()
        }.start()
    }
    return v
}

/** Ícones desenhados no próprio app (sem arquivos externos). */
class IconView(ctx: Context, var kind: Int, var color: Int = Color.WHITE) : View(ctx) {
    companion object {
        const val HOME = 0
        const val TARGET = 1
        const val FLASK = 2
        const val MAP = 3
        const val GEAR = 4
        const val PLAY = 5
        const val STOP = 6
        const val CLOSE = 7
        const val TRASH = 8
        const val DROP = 9
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = s * 0.32f
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.09f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        val path = Path()
        when (kind) {
            HOME -> {
                path.moveTo(cx - r, cy); path.lineTo(cx, cy - r); path.lineTo(cx + r, cy)
                path.moveTo(cx - r * 0.75f, cy - r * 0.1f); path.lineTo(cx - r * 0.75f, cy + r)
                path.lineTo(cx + r * 0.75f, cy + r); path.lineTo(cx + r * 0.75f, cy - r * 0.1f)
                c.drawPath(path, p)
            }
            TARGET -> {
                c.drawCircle(cx, cy, r, p)
                c.drawCircle(cx, cy, r * 0.3f, p)
                c.drawLine(cx, cy - r * 1.3f, cx, cy - r * 0.7f, p)
                c.drawLine(cx, cy + r * 0.7f, cx, cy + r * 1.3f, p)
                c.drawLine(cx - r * 1.3f, cy, cx - r * 0.7f, cy, p)
                c.drawLine(cx + r * 0.7f, cy, cx + r * 1.3f, cy, p)
            }
            FLASK -> {
                path.moveTo(cx - r * 0.35f, cy - r); path.lineTo(cx - r * 0.35f, cy - r * 0.2f)
                path.lineTo(cx - r * 0.9f, cy + r * 0.85f); path.lineTo(cx + r * 0.9f, cy + r * 0.85f)
                path.lineTo(cx + r * 0.35f, cy - r * 0.2f); path.lineTo(cx + r * 0.35f, cy - r)
                path.moveTo(cx - r * 0.6f, cy - r); path.lineTo(cx + r * 0.6f, cy - r)
                c.drawPath(path, p)
            }
            MAP -> {
                c.drawCircle(cx, cy - r * 0.25f, r * 0.6f, p)
                path.moveTo(cx - r * 0.5f, cy + r * 0.2f); path.lineTo(cx, cy + r); path.lineTo(cx + r * 0.5f, cy + r * 0.2f)
                c.drawPath(path, p)
            }
            GEAR -> {
                c.drawCircle(cx, cy, r * 0.55f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val cs = Math.cos(a).toFloat()
                    val sn = Math.sin(a).toFloat()
                    c.drawLine(cx + cs * r * 0.8f, cy + sn * r * 0.8f, cx + cs * r * 1.15f, cy + sn * r * 1.15f, p)
                }
            }
            PLAY -> {
                p.style = Paint.Style.FILL
                path.moveTo(cx - r * 0.6f, cy - r); path.lineTo(cx + r, cy); path.lineTo(cx - r * 0.6f, cy + r); path.close()
                c.drawPath(path, p)
            }
            STOP -> {
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f), s * 0.08f, s * 0.08f, p)
            }
            CLOSE -> {
                c.drawLine(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, p)
                c.drawLine(cx + r * 0.7f, cy - r * 0.7f, cx - r * 0.7f, cy + r * 0.7f, p)
            }
            DROP -> {
                path.moveTo(cx, cy - r * 1.1f)
                path.cubicTo(cx + r * 0.2f, cy - r * 0.5f, cx + r * 0.95f, cy + r * 0.05f, cx + r * 0.65f, cy + r * 0.6f)
                path.quadTo(cx, cy + r * 1.2f, cx - r * 0.65f, cy + r * 0.6f)
                path.cubicTo(cx - r * 0.95f, cy + r * 0.05f, cx - r * 0.2f, cy - r * 0.5f, cx, cy - r * 1.1f)
                path.close()
                c.drawPath(path, p)
            }
            TRASH -> {
                c.drawLine(cx - r, cy - r * 0.6f, cx + r, cy - r * 0.6f, p)
                c.drawLine(cx - r * 0.3f, cy - r * 0.95f, cx + r * 0.3f, cy - r * 0.95f, p)
                path.moveTo(cx - r * 0.7f, cy - r * 0.6f); path.lineTo(cx - r * 0.55f, cy + r)
                path.lineTo(cx + r * 0.55f, cy + r); path.lineTo(cx + r * 0.7f, cy - r * 0.6f)
                c.drawPath(path, p)
            }
        }
    }
}
