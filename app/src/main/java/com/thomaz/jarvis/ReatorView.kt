package com.thomaz.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

class ReatorView(ctx: Context) : View(ctx) {

    var modo: Int = 0
        set(valor) {
            field = valor
            invalidate()
        }

    private val traco = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brilho = Paint(Paint.ANTI_ALIAS_FLAG)
    private val area = RectF()
    private var angulo = 0f
    private var fase = 0f
    private var ultimo = 0L

    init {
        traco.style = Paint.Style.STROKE
        traco.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val agora = System.nanoTime()
        val dt = if (ultimo == 0L) 0.016f else ((agora - ultimo) / 1e9f).coerceAtMost(0.05f)
        ultimo = agora

        val vel = when (modo) {
            1 -> 140f
            2 -> 260f
            3 -> 90f
            else -> 30f
        }
        angulo = (angulo + vel * dt) % 360f
        fase += dt * (if (modo == 0) 1.2f else 3.2f)

        val cor = if (modo == 2) Color.parseColor("#FFB300") else Color.parseColor("#00E5FF")
        val cr = Color.red(cor)
        val cg = Color.green(cor)
        val cb = Color.blue(cor)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy) * 0.94f
        val pulso = 0.5f + 0.5f * Math.sin(fase.toDouble()).toFloat()
        val forca = if (modo == 0) 0.35f + 0.15f * pulso else 0.65f + 0.35f * pulso

        brilho.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.argb((110 * forca).toInt(), cr, cg, cb), Color.argb(0, cr, cg, cb)),
            floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, brilho)

        traco.color = cor
        traco.alpha = (255 * (0.45f + 0.55f * forca)).toInt()

        traco.strokeWidth = r * 0.02f
        canvas.drawCircle(cx, cy, r * 0.96f, traco)

        area.set(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f)
        traco.strokeWidth = r * 0.05f
        for (i in 0 until 3) canvas.drawArc(area, angulo + i * 120f, 70f, false, traco)

        area.set(cx - r * 0.66f, cy - r * 0.66f, cx + r * 0.66f, cy + r * 0.66f)
        traco.strokeWidth = r * 0.03f
        for (i in 0 until 8) canvas.drawArc(area, -angulo * 1.6f + i * 45f, 28f, false, traco)

        traco.strokeWidth = r * 0.07f
        for (i in 0 until 10) {
            val a = Math.toRadians(i * 36.0 + angulo * 0.2)
            val ca = Math.cos(a).toFloat()
            val sa = Math.sin(a).toFloat()
            canvas.drawLine(cx + ca * r * 0.40f, cy + sa * r * 0.40f, cx + ca * r * 0.54f, cy + sa * r * 0.54f, traco)
        }

        traco.strokeWidth = r * 0.025f
        canvas.drawCircle(cx, cy, r * 0.32f, traco)

        val nuc = r * 0.26f * (0.9f + 0.15f * pulso)
        brilho.shader = RadialGradient(
            cx, cy, nuc,
            intArrayOf(Color.WHITE, cor, Color.argb(0, cr, cg, cb)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, nuc, brilho)

        postInvalidateOnAnimation()
    }
}
