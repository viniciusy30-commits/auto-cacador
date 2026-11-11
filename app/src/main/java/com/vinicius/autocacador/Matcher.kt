package com.vinicius.autocacador

import android.graphics.Bitmap
import java.util.Random

class Gray(val w: Int, val h: Int, val d: ByteArray)

/** Compara uma "foto" (recorte) com a tela, ignorando o fundo quando possível. */
object Matcher {
    class Tpl(val w: Int, val h: Int, val xs: IntArray, val ys: IntArray, val v: IntArray)
    class Hit(val x: Int, val y: Int, val score: Float)

    /** Diferença de cinza aceita para um ponto da foto "bater" com a tela. */
    private const val TOL = 30

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

    /**
     * mask = true (nomes/textos): guarda só os pontos do TEXTO (o grupo de contraste mais numeroso)
     * e uma parte menor do contorno do outro lado. Assim o cenário atrás do nome pesa pouco e o
     * mesmo nome é achado em qualquer chão.
     * mask = false (itens): usa a imagem inteira.
     */
    fun makeTpl(g: Gray, mask: Boolean): Tpl? {
        val n = g.w * g.h
        if (n < 20) return null
        val all = IntArray(n) { g.d[it].toInt() and 255 }
        val rnd = Random(7)
        val idx = ArrayList<Int>()
        if (!mask) {
            for (i in 0 until n) idx.add(i)
            idx.shuffle(rnd)
        } else {
            val med = all.sortedArray()[n / 2]
            val hi = ArrayList<Int>()
            val lo = ArrayList<Int>()
            for (i in 0 until n) {
                val dv = all[i] - med
                if (dv >= 40) hi.add(i) else if (dv <= -40) lo.add(i)
            }
            if (hi.size + lo.size < 12) return null
            val main = if (hi.size >= lo.size) hi else lo
            val side = if (hi.size >= lo.size) lo else hi
            main.shuffle(rnd)
            side.shuffle(rnd)
            val mainTake = minOf(main.size, 380)
            val sideTake = minOf(side.size, mainTake / 3, 120)
            for (k in 0 until mainTake) idx.add(main[k])
            for (k in 0 until sideTake) idx.add(side[k])
            idx.shuffle(rnd)
        }
        if (idx.size < 12) return null
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

    private fun missAt(d: ByteArray, gw: Int, t: Tpl, x: Int, y: Int, limit: Int): Int {
        var miss = 0
        for (i in t.v.indices) {
            val df = (d[(y + t.ys[i]) * gw + x + t.xs[i]].toInt() and 255) - t.v[i]
            if (df > TOL || df < -TOL) {
                miss++
                if (miss > limit) return miss
            }
        }
        return miss
    }

    /**
     * Todos os acertos, sem repetir o mesmo lugar. Busca de 2 em 2 pixels (4x mais rápida)
     * com margem maior e depois refina cada candidato pixel a pixel.
     */
    fun findAll(g: Gray, t: Tpl, x0: Int, y0: Int, x1: Int, y1: Int, minScore: Float): List<Hit> {
        val n = t.v.size
        if (n < 12) return emptyList()
        val maxMiss = ((1f - minScore) * n).toInt()
        val coarseMax = maxMiss + maxOf(3, n / 12)
        val stage = minOf(24, n)
        val stageAllowed = (coarseMax.toFloat() * stage / n * 1.6f).toInt() + 2
        val d = g.d
        val gw = g.w
        val xs0 = maxOf(x0, 0)
        val ys0 = maxOf(y0, 0)
        val xe = minOf(x1, g.w) - t.w
        val ye = minOf(y1, g.h) - t.h
        val cand = ArrayList<IntArray>()
        var y = ys0
        while (y <= ye && cand.size < 1500) {
            var x = xs0
            while (x <= xe) {
                var miss = 0
                var i = 0
                while (i < n) {
                    val df = (d[(y + t.ys[i]) * gw + x + t.xs[i]].toInt() and 255) - t.v[i]
                    if (df > TOL || df < -TOL) {
                        miss++
                        if (miss > coarseMax) break
                    }
                    i++
                    if (i == stage && miss > stageAllowed) break
                }
                if (i >= n && miss <= coarseMax) cand.add(intArrayOf(x, y, miss))
                x += 2
            }
            y += 2
        }
        val raw = ArrayList<Hit>()
        for (c in cand) {
            var bm = Int.MAX_VALUE
            var bx = c[0]
            var by = c[1]
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val xx = c[0] + dx
                    val yy = c[1] + dy
                    if (xx < xs0 || yy < ys0 || xx > xe || yy > ye) continue
                    val m = missAt(d, gw, t, xx, yy, minOf(bm, maxMiss))
                    if (m < bm) {
                        bm = m
                        bx = xx
                        by = yy
                    }
                }
            }
            if (bm <= maxMiss) raw.add(Hit(bx + t.w / 2, by + t.h / 2, 1f - bm.toFloat() / n))
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
