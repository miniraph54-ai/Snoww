package com.snowdroid

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Choreographer
import android.view.View
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class SnowView(ctx: Context) : View(ctx),
    SharedPreferences.OnSharedPreferenceChangeListener, Choreographer.FrameCallback {

    private class Flake(var x: Float, var y: Float, var r: Float, var vy: Float, var ph: Float)
    private class Man(var x: Float, var dir: Int, var born: Long, var cd: Float) { var y = 0f }
    private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float, var t: Float)

    private val prefs = ctx.getSharedPreferences("snow", Context.MODE_PRIVATE)
    private val d = resources.displayMetrics.density
    private val rnd = Random()
    private val flakes = ArrayList<Flake>()
    private val men = ArrayList<Man>()
    private val balls = ArrayList<Ball>()
    private var pile = FloatArray(0)
    private val colW = 6f * d
    private var santaX = Float.NaN
    private var santaY = 0f

    private var intensity = 45
    private var pileOn = true
    private var menOn = true
    private var fightsOn = true
    private var santaOn = true

    private var last = 0L
    private var nextMan = 0L
    private var paused = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val EDGE = 0xFF9FB3D1.toInt()
    private val RED = 0xFFD32F2F.toInt()
    private val DARK = 0xFF1D1D22.toInt()
    private val BROWN = 0xFF6B4A2B.toInt()

    private fun load() {
        intensity = prefs.getInt("intensity", 45)
        pileOn = prefs.getBoolean("pile", true)
        menOn = prefs.getBoolean("men", true)
        fightsOn = prefs.getBoolean("fights", true)
        santaOn = prefs.getBoolean("santa", true)
        if (!pileOn) pile.fill(0f)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = load()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(this)
        load()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val n = (w / colW).toInt() + 2
        if (pile.size != n) {
            val base = pile
            pile = FloatArray(n) { i ->
                when {
                    i < base.size -> base[i]
                    pileOn -> 8f * d + sin(i * 0.4f) * 2f * d + rnd.nextFloat() * d
                    else -> 0f
                }
            }
        }
        if (flakes.isEmpty()) {
            repeat((intensity * 2.2f).toInt()) {
                flakes.add(newFlake(w.toFloat(), rnd.nextFloat() * h))
            }
        }
    }

    fun pause() { paused = true; Choreographer.getInstance().removeFrameCallback(this) }

    fun resume() {
        if (!paused) return
        paused = false
        last = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun santa(force: Boolean) {
        if ((!santaOn && !force) || !santaX.isNaN() || width == 0) return
        santaX = -170f * d * 0.9f
        santaY = height * (0.08f + rnd.nextFloat() * 0.15f)
    }

    fun shake() {
        if (!prefs.getBoolean("shake", true) || pile.isEmpty()) return
        val floor = height - 12f * d
        var spawned = 0
        for (i in pile.indices) {
            if (pile[i] > 1.5f * d && rnd.nextFloat() < 0.5f && spawned < 120) {
                flakes.add(Flake(i * colW, floor - pile[i], d * 1.5f, d * (1f + rnd.nextFloat() * 2f), rnd.nextFloat() * 6f))
                spawned++
            }
        }
        pile.fill(0f)
    }

    private fun newFlake(w: Float, y: Float): Flake {
        val rr = 1.2f + rnd.nextInt(5) * 0.5f
        return Flake(rnd.nextFloat() * w, y, rr * d, (0.6f + rr * 0.35f + rnd.nextFloat() * 0.4f) * d, rnd.nextFloat() * 6.28f)
    }

    private fun col(x: Float) = (x / colW).toInt().coerceIn(0, max(0, pile.size - 1))

    private fun addSnow(c0: Int) {
        var c = c0
        var n = 0
        while (n++ < 30) {
            if (c > 0 && pile[c] - pile[c - 1] > 1.2f * d) c--
            else if (c < pile.size - 1 && pile[c] - pile[c + 1] > 1.2f * d) c++
            else break
        }
        if (pile[c] < 34f * d) pile[c] += 0.4f * d
    }

    override fun doFrame(ns: Long) {
        if (paused) return
        val now = ns / 1_000_000
        if (last == 0L) last = now
        val dt = now - last
        if (dt >= 20) {
            last = now
            step(min(dt, 100L) / 33f, now)
            invalidate()
        }
        Choreographer.getInstance().postFrameCallbackDelayed(this, 22)
    }

    private fun step(k: Float, now: Long) {
        if (width == 0 || pile.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val floor = h - 12f * d
        val target = (intensity * 2.2f).toInt()

        var added = 0
        while (flakes.size < target && added < 4) {
            flakes.add(newFlake(w, -rnd.nextFloat() * 40f * d)); added++
        }
        val wind = sin(now / 4000.0).toFloat() * 0.4f
        var i = flakes.size - 1
        while (i >= 0) {
            val f = flakes[i]
            f.ph += 0.06f * k
            f.x += (sin(f.ph) * 0.5f + wind * (f.r / d) * 0.35f) * d * k
            f.y += f.vy * k
            var gone = false
            if (pileOn && f.y >= floor - pile[col(f.x)]) { addSnow(col(f.x)); gone = true }
            else if (f.y > h + 5f * d || f.x < -20f * d || f.x > w + 20f * d) gone = true
            if (gone) {
                if (flakes.size > target) flakes.removeAt(i)
                else { f.x = rnd.nextFloat() * w; f.y = -rnd.nextFloat() * 40f * d }
            }
            i--
        }

        if (!menOn) { men.clear(); balls.clear() } else {
            if (nextMan == 0L) nextMan = now + 3000
            if (now >= nextMan && men.size < 3) {
                men.add(Man(w * (0.15f + rnd.nextFloat() * 0.7f), if (rnd.nextBoolean()) 1 else -1,
                    now, 60f + rnd.nextFloat() * 90f))
                nextMan = now + 15000 + rnd.nextInt(15000)
            }
            val sc = d * 0.8f
            val it = men.iterator()
            while (it.hasNext()) {
                val m = it.next()
                m.x += m.dir * 0.15f * d * k
                if (m.x < w * 0.1f || m.x > w * 0.9f) m.dir = -m.dir
                m.y = floor - (if (pileOn) pile[col(m.x)] else 0f)
                if (now - m.born > 60000) {
                    repeat(6) {
                        flakes.add(Flake(m.x + (rnd.nextFloat() - 0.5f) * 20f * d, m.y - rnd.nextFloat() * 40f * d,
                            1.5f * d, d * (0.5f + rnd.nextFloat()), rnd.nextFloat() * 6f))
                    }
                    it.remove()
                }
            }
            if (fightsOn && men.size > 1) {
                for (m in men) {
                    m.cd -= k
                    if (m.cd <= 0f) {
                        val tg = men.filter { o -> o !== m }.random()
                        val tt = 40f
                        val g = 0.22f * d
                        val sx = m.x
                        val sy = m.y - 40f * sc
                        val tx = tg.x + tg.dir * 0.15f * d * tt
                        val ty = tg.y - 30f * sc
                        balls.add(Ball(sx, sy, (tx - sx) / tt, (ty - sy - 0.5f * g * tt * tt) / tt, tt))
                        m.cd = 80f + rnd.nextFloat() * 140f
                    }
                }
            }
            val bi = balls.iterator()
            while (bi.hasNext()) {
                val b = bi.next()
                b.x += b.vx * k
                b.y += b.vy * k
                b.vy += 0.22f * d * k
                b.t -= k
                if (b.t <= 0f) {
                    repeat(5) {
                        flakes.add(Flake(b.x + (rnd.nextFloat() - 0.5f) * 12f * d, b.y + (rnd.nextFloat() - 0.5f) * 8f * d,
                            1.4f * d, d * (0.5f + rnd.nextFloat()), rnd.nextFloat() * 6f))
                    }
                    bi.remove()
                }
            }
        }

        if (!santaX.isNaN()) {
            santaX += 4.5f * d * k
            if (santaX > w) santaX = Float.NaN
        }
    }

    // ---------- dessin ----------
    private fun ov(c: Canvas, l: Float, t: Float, r: Float, b: Float, fill: Int, edge: Int = 0) {
        p.style = Paint.Style.FILL; p.color = fill
        c.drawOval(l, t, r, b, p)
        if (edge != 0) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = edge
            c.drawOval(l, t, r, b, p)
        }
    }

    private fun ln(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, col: Int, w: Float) {
        p.style = Paint.Style.STROKE; p.strokeWidth = w; p.color = col; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(x1, y1, x2, y2, p)
    }

    private fun rc(c: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int) {
        p.style = Paint.Style.FILL; p.color = col
        c.drawRect(l, t, r, b, p)
    }

    private fun poly(c: Canvas, col: Int, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.close()
        p.style = Paint.Style.FILL; p.color = col
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val floor = h - 12f * d
        if (pileOn && pile.isNotEmpty()) drawPile(c, w, h, floor)
        for (m in men) drawMan(c, m.x, m.y)
        for (b in balls) ov(c, b.x - 3.5f * d, b.y - 3.5f * d, b.x + 3.5f * d, b.y + 3.5f * d, WHITE, EDGE)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = 0x99A9BDDB.toInt()
        for (f in flakes) c.drawCircle(f.x, f.y, f.r, p)
        p.style = Paint.Style.FILL; p.color = WHITE
        for (f in flakes) c.drawCircle(f.x, f.y, f.r, p)
        if (!santaX.isNaN()) drawSanta(c)
    }

    private fun drawPile(c: Canvas, w: Float, h: Float, floor: Float) {
        val n = pile.size
        path.reset()
        path.moveTo(0f, h + 2f)
        for (i in 0 until n) {
            val s = (pile[max(i - 2, 0)] + 2 * pile[max(i - 1, 0)] + 3 * pile[i] +
                2 * pile[min(i + 1, n - 1)] + pile[min(i + 2, n - 1)]) / 9f
            path.lineTo(i * colW, floor - s)
        }
        path.lineTo(w, h + 2f)
        path.close()
        p.style = Paint.Style.FILL; p.color = WHITE
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0xFFC9D7EA.toInt()
        c.drawPath(path, p)
    }

    private fun drawMan(c: Canvas, x: Float, y: Float) {
        c.save(); c.translate(x, y); val s = d * 0.8f; c.scale(s, s)
        ln(c, -14f, -22f, -27f, -36f, BROWN, 2f); ln(c, -22f, -31f, -27f, -30f, BROWN, 2f)
        ln(c, 14f, -22f, 27f, -36f, BROWN, 2f); ln(c, 22f, -31f, 27f, -30f, BROWN, 2f)
        ov(c, -15f, -30f, 15f, 0f, WHITE, EDGE)
        ov(c, -11f, -51f, 11f, -29f, WHITE, EDGE)
        ov(c, -2f, -22f, 2f, -18f, 0xFF222222.toInt()); ov(c, -2f, -13f, 2f, -9f, 0xFF222222.toInt())
        ov(c, -12f, -35f, 12f, -27f, RED, 0xFF9C1F1F.toInt())
        poly(c, RED, 3f, -30f, 10f, -30f, 13f, -15f, 6f, -15f)
        ov(c, -5f, -45f, -2f, -42f, 0xFF222222.toInt()); ov(c, 2f, -45f, 5f, -42f, 0xFF222222.toInt())
        poly(c, 0xFFFF8A1F.toInt(), 0f, -41f, 9f, -39f, 0f, -37f)
        for ((dx, dy) in listOf(-4f to 0f, -2f to 1f, 2f to 1f, 4f to 0f)) {
            ov(c, dx - 1f, -36f + dy, dx + 1f, -34f + dy, 0xFF222222.toInt())
        }
        ln(c, -13f, -50f, 13f, -50f, DARK, 3f)
        rc(c, -8f, -65f, 8f, -50f, DARK)
        rc(c, -8f, -56f, 8f, -52f, RED)
        c.restore()
    }

    private fun drawSanta(c: Canvas) {
        c.save()
        c.translate(santaX, santaY + sin(santaX * 0.01f) * 8f * d)
        val s = d * 0.9f; c.scale(s, s)
        val wood = 0xFF8B5A2B.toInt(); val dk = 0xFF5A3A1B.toInt()
        ln(c, 70f, 20f, 95f, 20f, BROWN, 1.5f)
        ln(c, 0f, 34f, 70f, 34f, wood, 3f)
        poly(c, 0xFFC0261C.toInt(), 5f, 10f, 70f, 10f, 60f, 30f, 15f, 30f)
        ov(c, 28f, -9f, 42f, 5f, 0xFFF5C9A0.toInt())
        ov(c, 29f, -1f, 41f, 11f, WHITE, 0xFFCCCCCC.toInt())
        poly(c, RED, 27f, -4f, 43f, -4f, 36f, -22f)
        ov(c, 33f, -26f, 39f, -20f, WHITE)
        ov(c, 95f, 12f, 125f, 27f, wood, dk)
        ov(c, 122f, 6f, 134f, 17f, wood, dk)
        ov(c, 132f, 9f, 137f, 14f, 0xFFFF2A2A.toInt())
        ln(c, 100f, 25f, 98f, 38f, dk, 2f); ln(c, 120f, 25f, 122f, 38f, dk, 2f)
        ln(c, 126f, 6f, 124f, -4f, dk, 2f); ln(c, 124f, -4f, 130f, -8f, dk, 2f)
        c.restore()
    }
}
