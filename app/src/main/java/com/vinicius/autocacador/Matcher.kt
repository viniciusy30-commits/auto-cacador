package com.vinicius.autocacador

import android.graphics.Bitmap
import java.util.Random

class Gray(val w: Int, val h: Int, val d: ByteArray)

/** Compara uma "foto" (recorte) com a tela, ignorando o fundo quando possível. */
object Matcher {
    class Tpl(val w: Int, val h: Int, val xs: IntArray, val ys: IntArray, val v: IntArray)
    class Hit(val x: Int, val y: Int, val score: Float)

    fun toGray(b: Bitmap, s: Int): Gray {
        val w = b.width / s
        val h = b.height / s
        val out = ByteArray(w * h)
        val row = IntArray(b.width * s)
        for (y in 0 until h) {
            b.getPixels(row, 0, b.width, 0, y * s, b.width, s)
            for (x in 0 until w) {
                var sum = 0
                for (dy in 0 until s) {
                    for (dx in 0 until s) {
                        val c = row[dy * b.width + x * s + dx]
                        sum += (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) shr 8
                    }
                }
                out[y * w + x] = (sum / (s * s)).toByte()
            }
        }
        return Gray(w, h, out)
    }

    /** mask = true: usa só os pixels que contrastam com o fundo (bom para texto/nome). */
    fun makeTpl(g: Gray, mask: Boolean): Tpl? {
        val n = g.w * g.h
        if (n < 20) return null
        val all = IntArray(n) { g.d[it].toInt() and 255 }
        val sorted = all.sortedArray()
        val med = sorted[n / 2]
        val idx = ArrayList<Int>()
        for (i in 0 until n) {
            if (!mask || Math.abs(all[i] - med) >= 40) idx.add(i)
        }
        if (idx.size < 12) return null
        idx.shuffle(Random(7))
        val m = minOf(idx.size, 500)
        val xs = IntArray(m)
        val ys = IntArray(m)
        val vs = IntArray(m)
        for (k in 0 until m) {
            val i = idx[k]
            xs[k] = i % g.w
            ys[k] = i / g.w
            vs[k] = all[i]
        }
        return Tpl(g.w, g.h, xs, ys, vs)
    }

    fun find(g: Gray, t: Tpl, x0: Int, y0: Int, x1: Int, y1: Int, minScore: Float): Hit? {
        val n = t.v.size
        if (n < 12) return null
        val tol = 30
        val maxMiss = ((1f - minScore) * n).toInt()
        var bestMiss = maxMiss + 1
        var best: Hit? = null
        val stage = minOf(24, n)
        val stageAllowed = (maxMiss.toFloat() * stage / n * 1.6f).toInt() + 2
        val d = g.d
        val gw = g.w
        var y = y0
        while (y <= y1 - t.h) {
            var x = x0
            while (x <= x1 - t.w) {
                var miss = 0
                var i = 0
                while (i < n) {
                    val gv = d[(y + t.ys[i]) * gw + x + t.xs[i]].toInt() and 255
                    val df = gv - t.v[i]
                    if (df > tol || df < -tol) {
                        miss++
                        if (miss >= bestMiss) break
                    }
                    i++
                    if (i == stage && miss > stageAllowed) break
                }
                if (i >= n && miss < bestMiss) {
                    bestMiss = miss
                    best = Hit(x + t.w / 2, y + t.h / 2, 1f - miss.toFloat() / n)
                }
                x++
            }
            y++
        }
        return best
    }

    /** Todos os acertos, sem repetir o mesmo lugar. */
    fun findAll(g: Gray, t: Tpl, x0: Int, y0: Int, x1: Int, y1: Int, minScore: Float): List<Hit> {
        val n = t.v.size
        if (n < 12) return emptyList()
        val tol = 30
        val maxMiss = ((1f - minScore) * n).toInt()
        val stage = minOf(24, n)
        val stageAllowed = (maxMiss.toFloat() * stage / n * 1.6f).toInt() + 2
        val d = g.d
        val gw = g.w
        val raw = ArrayList<Hit>()
        var y = y0
        while (y <= y1 - t.h && raw.size < 3000) {
            var x = x0
            while (x <= x1 - t.w) {
                var miss = 0
                var i = 0
                while (i < n) {
                    val df = (d[(y + t.ys[i]) * gw + x + t.xs[i]].toInt() and 255) - t.v[i]
                    if (df > tol || df < -tol) {
                        miss++
                        if (miss > maxMiss) break
                    }
                    i++
                    if (i == stage && miss > stageAllowed) break
                }
                if (i >= n && miss <= maxMiss) raw.add(Hit(x + t.w / 2, y + t.h / 2, 1f - miss.toFloat() / n))
                x++
            }
            y++
        }
        raw.sortByDescending { it.score }
        val out = ArrayList<Hit>()
        for (h in raw) {
            if (out.none { Math.abs(it.x - h.x) < t.w / 2 + 2 && Math.abs(it.y - h.y) < t.h / 2 + 2 }) out.add(h)
            if (out.size >= 12) break
        }
        return out
    }

    /** Quanto da barra (0..100) ainda está cheio, medindo da esquerda para a direita. */
    fun fillPct(b: Bitmap, ax: Int, ay: Int, bx: Int, by: Int, ref: Int): Int {
        val n = 24
        var ok = 0
        for (k in 0 until n) {
            val x = ax + (bx - ax) * k / (n - 1)
            val y = ay + (by - ay) * k / (n - 1)
            if (!far(avg(b, x, y), ref, 110)) ok++
        }
        return ok * 100 / n
    }

    /** Cor média 3x3 ao redor do ponto. */
    fun avg(b: Bitmap, px: Int, py: Int): Int {
        var r = 0
        var g = 0
        var bl = 0
        var k = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                val x = (px + dx).coerceIn(0, b.width - 1)
                val y = (py + dy).coerceIn(0, b.height - 1)
                val c = b.getPixel(x, y)
                r += (c shr 16) and 255
                g += (c shr 8) and 255
                bl += c and 255
                k++
            }
        }
        return (0xFF shl 24) or ((r / k) shl 16) or ((g / k) shl 8) or (bl / k)
    }

    fun far(a: Int, b: Int, lim: Int = 90): Boolean {
        val d = Math.abs(((a shr 16) and 255) - ((b shr 16) and 255)) +
            Math.abs(((a shr 8) and 255) - ((b shr 8) and 255)) +
            Math.abs((a and 255) - (b and 255))
        return d > lim
    }
}
