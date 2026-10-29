package com.vinicius.autocacador

/**
 * Ponto da rota. x e y são a posição em FRAÇÃO da largura da tela, relativa ao ponto onde a rota
 * começou (0, 0). hold = ponto de caça: o bot espera ali por monstros.
 */
class Waypoint(var x: Float, var y: Float, var hold: Boolean)

/** Região da tela usada para medir o movimento: foge de botões e barras (esquerda, direita, topo e base). */
fun odoRegion(g: Gray, cfg: Cfg): IntArray {
    val x0 = maxOf(g.w * cfg.mLeft / 100, g.w * 15 / 100)
    val x1 = minOf(g.w - g.w * cfg.mRight / 100, g.w * 92 / 100)
    val y0 = maxOf(g.h * cfg.mTop / 100, g.h * 38 / 100)
    val y1 = minOf(g.h - g.h * cfg.mBottom / 100, g.h * 90 / 100)
    return intArrayOf(x0, y0, x1, y1)
}

/**
 * Odometria visual: o personagem fica no centro da tela e o cenário rola quando ele anda.
 * Medindo quanto o cenário rolou entre duas imagens, dá para somar o caminho percorrido e saber
 * "onde" o personagem está em relação ao ponto de partida (fx, fy, em fração da largura da tela).
 */
class Odo {
    var fx = 0f
    var fy = 0f
    /** Medições seguidas que falharam. */
    var lost = 0
    private var prev: Gray? = null
    private var prevBlur: Gray? = null
    private var lsx = 0
    private var lsy = 0

    private class Cand(val bx: Int, val by: Int, val v: Float)

    fun reset(x: Float = 0f, y: Float = 0f) {
        fx = x
        fy = y
        lost = 0
        prev = null
        prevBlur = null
        lsx = 0
        lsy = 0
    }

    /** Soma o movimento desde a imagem anterior. Devolve false se não conseguiu medir. */
    fun update(g: Gray, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        val gb = blur(g)
        val a = prev
        val ab = prevBlur
        prev = g
        prevBlur = gb
        if (a == null || ab == null || a.w != g.w || a.h != g.h) return true
        var r = measure(a, ab, g, gb, x0, y0, x1, y1, lsx, lsy)
        if (r == null && (lsx != 0 || lsy != 0)) r = measure(a, ab, g, gb, x0, y0, x1, y1, 0, 0)
        if (r == null) {
            lost++
            lsx = 0
            lsy = 0
            return false
        }
        lost = 0
        lsx = Math.round(r[0])
        lsy = Math.round(r[1])
        // o cenário anda para um lado quando o personagem anda para o outro
        fx -= r[0] / g.w
        fy -= r[1] / g.w
        return true
    }

    private fun blur(g: Gray): Gray {
        val w = g.w
        val h = g.h
        val tmp = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var s = 0
                for (k in -2..2) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    s += g.d[row + xx].toInt() and 255
                }
                tmp[row + x] = s
            }
        }
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0
                for (k in -2..2) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    s += tmp[yy * w + x]
                }
                out[y * w + x] = (s / 25).toByte()
            }
        }
        return Gray(w, h, out)
    }

    private fun variance(g: Gray, bx: Int, by: Int, bs: Int): Float {
        var s = 0L
        var s2 = 0L
        var n = 0
        var y = 0
        while (y < bs) {
            var x = 0
            while (x < bs) {
                val v = g.d[(by + y) * g.w + bx + x].toInt() and 255
                s += v
                s2 += v * v
                n++
                x += 2
            }
            y += 2
        }
        val mean = s.toFloat() / n
        return s2.toFloat() / n - mean * mean
    }

    private fun sad(a: Gray, b: Gray, ax: Int, ay: Int, bx: Int, by: Int, bs: Int, limit: Int): Int {
        var s = 0
        var y = 0
        while (y < bs) {
            val ra = (ay + y) * a.w + ax
            val rb = (by + y) * b.w + bx
            var x = 0
            while (x < bs) {
                val d = (a.d[ra + x].toInt() and 255) - (b.d[rb + x].toInt() and 255)
                s += if (d < 0) -d else d
                x += 2
            }
            if (s >= limit) return s
            y += 2
        }
        return s
    }

    /** Onde o bloco (bx, by) da imagem anterior foi parar na atual: [dx, dy], ou null se não achou bem. */
    private fun matchBlock(a: Gray, ab: Gray, b: Gray, bb: Gray, bx: Int, by: Int, bs: Int, pcx: Int, pcy: Int): IntArray? {
        val rad = 110
        var best = Int.MAX_VALUE
        var bsx = 0
        var bsy = 0
        // busca grossa na imagem suavizada (a "bacia" de erro fica larga e o passo de 4 px não perde o mínimo)
        var sy = pcy - rad
        while (sy <= pcy + rad) {
            val ty = by + sy
            if (ty >= 0 && ty + bs <= b.h) {
                var sx = pcx - rad
                while (sx <= pcx + rad) {
                    val tx = bx + sx
                    if (tx >= 0 && tx + bs <= b.w) {
                        val s = sad(ab, bb, bx, by, tx, ty, bs, best)
                        if (s < best) {
                            best = s
                            bsx = sx
                            bsy = sy
                        }
                    }
                    sx += 4
                }
            }
            sy += 4
        }
        if (best == Int.MAX_VALUE) return null
        // refina pixel a pixel na imagem original
        best = Int.MAX_VALUE
        val cx0 = bsx
        val cy0 = bsy
        for (dy in -4..4) {
            for (dx in -4..4) {
                val sx = cx0 + dx
                val sy2 = cy0 + dy
                val tx = bx + sx
                val ty = by + sy2
                if (tx < 0 || ty < 0 || tx + bs > b.w || ty + bs > b.h) continue
                val s = sad(a, b, bx, by, tx, ty, bs, best)
                if (s < best) {
                    best = s
                    bsx = sx
                    bsy = sy2
                }
            }
        }
        if (best > 20 * 144) return null
        return intArrayOf(bsx, bsy)
    }

    private fun measure(
        a: Gray, ab: Gray, b: Gray, bb: Gray,
        x0: Int, y0: Int, x1: Int, y1: Int, pcx: Int, pcy: Int
    ): FloatArray? {
        val bs = 24
        val cx = a.w / 2
        val cy = a.h / 2
        val cols = 7
        val rows = 5
        val rw = x1 - x0 - bs
        val rh = y1 - y0 - bs
        if (rw <= 0 || rh <= 0) return null
        val cands = ArrayList<Cand>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val bx = x0 + rw * c / (cols - 1)
                val by = y0 + rh * r / (rows - 1)
                if (bx < 0 || by < 0 || bx + bs > a.w || by + bs > a.h) continue
                // pula o personagem (e o nome dele), que fica parado no centro
                if (Math.abs(bx + bs / 2 - cx) < 60 && Math.abs(by + bs / 2 - cy) < 80) continue
                val v = variance(a, bx, by, bs)
                if (v >= 80f) cands.add(Cand(bx, by, v))
            }
        }
        cands.sortByDescending { it.v }
        val n = minOf(cands.size, 18)
        val rx = IntArray(n)
        val ry = IntArray(n)
        var m = 0
        for (i in 0 until n) {
            val cd = cands[i]
            val res = matchBlock(a, ab, b, bb, cd.bx, cd.by, bs, pcx, pcy) ?: continue
            rx[m] = res[0]
            ry[m] = res[1]
            m++
        }
        // votação: o cenário inteiro anda junto; botões parados e monstros isolados ficam de fora
        var bestCnt = 0
        var bestIdx = -1
        var nzCnt = 0
        var nzIdx = -1
        for (i in 0 until m) {
            var cnt = 0
            for (j in 0 until m) {
                if (Math.abs(rx[i] - rx[j]) <= 2 && Math.abs(ry[i] - ry[j]) <= 2) cnt++
            }
            if (cnt > bestCnt) {
                bestCnt = cnt
                bestIdx = i
            }
            val nz = Math.abs(rx[i]) >= 3 || Math.abs(ry[i]) >= 3
            if (nz && cnt > nzCnt) {
                nzCnt = cnt
                nzIdx = i
            }
        }
        val pick = if (nzIdx >= 0 && nzCnt >= 4) nzIdx else if (bestCnt >= 3) bestIdx else -1
        if (pick < 0) return null
        var sx = 0f
        var sy = 0f
        var k = 0
        for (j in 0 until m) {
            if (Math.abs(rx[pick] - rx[j]) <= 2 && Math.abs(ry[pick] - ry[j]) <= 2) {
                sx += rx[j]
                sy += ry[j]
                k++
            }
        }
        return floatArrayOf(sx / k, sy / k)
    }
}

/** Grava a rota enquanto você anda no jogo: guarda o caminho medido pela odometria. */
class RouteRecorder(private val svc: BotService) {
    @Volatile var active = false
    private val pts = ArrayList<Waypoint>()
    private val odo = Odo()
    @Volatile private var markReq = false

    fun count(): Int = synchronized(pts) { pts.size }

    fun start() {
        if (active) return
        synchronized(pts) { pts.clear() }
        odo.reset()
        markReq = false
        active = true
        Thread {
            try {
                loop()
            } catch (e: Throwable) {
                svc.toast("Erro na gravação: ${e.message}")
            } finally {
                active = false
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    /** Marca o lugar atual como ponto de caça (o bot vai esperar monstros aqui). */
    fun mark() { markReq = true }

    /** Para a gravação. Com save = true, guarda a rota nas configurações. */
    fun finish(save: Boolean) {
        active = false
        if (!save) return
        val copy = synchronized(pts) { ArrayList(pts) }
        if (copy.size < 2) return
        val cfg = Store.cfg
        cfg.route.clear()
        cfg.route.addAll(copy)
        cfg.routeOn = true
        Store.save()
        svc.toast("Rota salva: ${copy.size} pontos (${copy.count { it.hold }} de caça).")
    }

    private fun loop() {
        val cfg = Store.cfg
        var fails = 0
        while (active) {
            val t0 = System.currentTimeMillis()
            val bmp = svc.shot()
            if (bmp == null) {
                fails++
                if (fails == 10) svc.toast("Não consigo ver a tela do jogo.")
                Thread.sleep(400)
                continue
            }
            fails = 0
            val g = Matcher.toGray(bmp, 2)
            bmp.recycle()
            val rg = odoRegion(g, cfg)
            val ok = odo.update(g, rg[0], rg[1], rg[2], rg[3])
            if (!ok && odo.lost == 10) svc.toast("Não consigo medir o movimento: o chão está liso demais ou você andou rápido demais.")
            var cnt = 0
            var holds = 0
            synchronized(pts) {
                if (pts.isEmpty()) pts.add(Waypoint(0f, 0f, false))
                val last = pts[pts.size - 1]
                val d = Math.hypot((odo.fx - last.x).toDouble(), (odo.fy - last.y).toDouble()).toFloat()
                if (markReq) {
                    markReq = false
                    if (d < 0.015f) last.hold = true else pts.add(Waypoint(odo.fx, odo.fy, true))
                } else if (d > 0.08f) {
                    pts.add(Waypoint(odo.fx, odo.fy, false))
                }
                cnt = pts.size
                holds = pts.count { it.hold }
            }
            svc.recStatus("Gravando: $cnt pontos ($holds de caça). Ande pelo jogo.")
            val el = System.currentTimeMillis() - t0
            if (el < 200L) Thread.sleep(200L - el)
        }
    }
}
