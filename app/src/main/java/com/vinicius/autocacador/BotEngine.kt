package com.vinicius.autocacador

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.Random

class BotEngine(private val svc: BotService) {
    @Volatile var running = false
    private var th: Thread? = null

    private class T(val t: Target, val tpl: Matcher.Tpl)
    private class Found(val t: T, val hit: Matcher.Hit)

    private val sc = 2
    private val rnd = Random()

    fun start() {
        if (running) return
        running = true
        svc.runState(true)
        th = Thread {
            try {
                loop()
                svc.setStatus("Parado")
            } catch (e: Throwable) {
                svc.setStatus("Erro: ${e.message}")
            } finally {
                running = false
                svc.runState(false)
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() { running = false }

    private fun now(): Long = System.currentTimeMillis()

    private fun nap(ms: Long) {
        var left = ms
        while (running && left > 0) {
            val s = minOf(left, 100L)
            Thread.sleep(s)
            left -= s
        }
    }

    private fun load(items: Boolean): List<T> {
        val out = ArrayList<T>()
        for (t in Store.cfg.targets) {
            if (t.isItem != items || !t.enabled) continue
            val f = File(Store.tplDir(), t.file)
            if (!f.exists()) continue
            val b = BitmapFactory.decodeFile(f.path) ?: continue
            val g = Matcher.toGray(b, sc)
            b.recycle()
            val tpl = Matcher.makeTpl(g, !items) ?: continue
            out.add(T(t, tpl))
        }
        return out
    }

    /** Escolhe o acerto mais perto do centro (nearest) ou o mais parecido com a foto. */
    private fun best(
        g: Gray, list: List<T>, x0: Int, y0: Int, x1: Int, y1: Int, min: Float,
        nearest: Boolean, skip: (Int, Int) -> Boolean
    ): Found? {
        val cx = (x0 + x1) / 2
        val cy = (y0 + y1) / 2
        var b: Found? = null
        var bd = Long.MAX_VALUE
        for (t in list) {
            if (!running) break
            for (h in Matcher.findAll(g, t.tpl, x0, y0, x1, y1, min)) {
                if (skip(h.x * sc, h.y * sc)) continue
                val dx = (h.x - cx).toLong()
                val dy = (h.y - cy).toLong()
                val d = if (nearest) dx * dx + dy * dy else ((1f - h.score) * 1000000f).toLong()
                if (d < bd) {
                    bd = d
                    b = Found(t, h)
                }
            }
        }
        return b
    }

    private fun actions(bmp: Bitmap, monster: Boolean, lastUse: HashMap<String, Long>) {
        val n = now()
        val bw = bmp.width
        val bh = bmp.height
        for (a in Store.cfg.actions) {
            val b = a.btn ?: continue
            if (!a.enabled) continue
            if (n - (lastUse[a.id] ?: 0L) < (a.cooldownSec * 1000).toLong()) continue
            val fire: Boolean = if (a.type == ActionType.SKILL) {
                monster
            } else {
                val bar = a.bar
                val b2 = a.bar2
                if (bar == null) {
                    true
                } else if (b2 != null) {
                    Matcher.fillPct(bmp, (bar.fx * bw).toInt(), (bar.fy * bh).toInt(), (b2.fx * bw).toInt(), (b2.fy * bh).toInt(), a.barColor) < a.pct
                } else {
                    Matcher.far(Matcher.avg(bmp, (bar.fx * bw).toInt(), (bar.fy * bh).toInt()), a.barColor)
                }
            }
            if (fire) {
                svc.tap(b.fx * bw, b.fy * bh)
                lastUse[a.id] = now()
                nap(150)
            }
        }
    }

    private fun loop() {
        val cfg = Store.cfg
        val mons = load(false)
        val items = load(true)
        if (mons.isEmpty()) {
            svc.setStatus("Nenhum monstro válido. Capture de novo na aba Alvos.")
            return
        }
        svc.setStatus("Rodando… procurando monstro")
        var lastActivity = now()
        var lastClick = 0L
        var travelUntil = 0L
        var destIdx = 0
        var fails = 0
        var focusX = -9999
        var focusY = -9999
        var focusSince = 0L
        val lastUse = HashMap<String, Long>()
        val monIgn = ArrayList<LongArray>()
        val itemIgn = ArrayList<LongArray>()
        val clicks = HashMap<Long, Int>()

        while (running) {
            val t0 = now()
            val bmp = svc.shot()
            if (bmp == null) {
                fails++
                if (fails >= 8) {
                    svc.setStatus("Não consigo ver a tela (veja Ajustes > Ajuda)")
                    break
                }
                nap(500)
                continue
            }
            fails = 0
            val bw = bmp.width
            val bh = bmp.height
            val g = Matcher.toGray(bmp, sc)
            val x0 = g.w * cfg.mLeft / 100
            val x1 = g.w - g.w * cfg.mRight / 100
            val y0 = g.h * cfg.mTop / 100
            val y1 = g.h - g.h * cfg.mBottom / 100
            monIgn.removeAll { it[2] < t0 }
            itemIgn.removeAll { it[2] < t0 }
            val mon = best(g, mons, x0, y0, x1, y1, cfg.monScore / 100f, cfg.nearest) { x, y ->
                monIgn.any { Math.abs(it[0] - x) < 70 && Math.abs(it[1] - y) < 70 }
            }
            val item: Found? = if (items.isNotEmpty() && running) {
                best(g, items, x0, y0, x1, y1, cfg.itemScore / 100f, true) { x, y ->
                    itemIgn.any { Math.abs(it[0] - x) < 50 && Math.abs(it[1] - y) < 50 }
                }
            } else null
            if (item == null) clicks.clear()
            actions(bmp, mon != null, lastUse)
            bmp.recycle()
            if (!running) break

            if (item != null && (cfg.lootFirst || mon == null)) {
                val rx = item.hit.x * sc
                val ry = item.hit.y * sc
                val key = (rx / 40).toLong() * 100000L + (ry / 40)
                val c = (clicks[key] ?: 0) + 1
                clicks[key] = c
                if (c > 3) {
                    itemIgn.add(longArrayOf(rx.toLong(), ry.toLong(), t0 + 20000))
                    clicks.remove(key)
                } else {
                    svc.setStatus("Pegando: ${item.t.t.name}")
                    svc.tap(rx.toFloat(), ry.toFloat())
                    lastActivity = now()
                    travelUntil = 0
                    nap(700)
                }
            } else if (mon != null) {
                travelUntil = 0
                lastActivity = t0
                val mx = mon.hit.x * sc
                val my = mon.hit.y * sc
                if (Math.abs(mx - focusX) < 90 && Math.abs(my - focusY) < 90) {
                    if (cfg.giveUpSec > 0 && t0 - focusSince > cfg.giveUpSec * 1000L) {
                        monIgn.add(longArrayOf(mx.toLong(), my.toLong(), t0 + 15000))
                        focusX = -9999
                        focusY = -9999
                        svc.setStatus("Desisti deste monstro, procurando outro…")
                    }
                } else {
                    focusX = mx
                    focusY = my
                    focusSince = t0
                }
                if (t0 - lastClick >= (cfg.reclickSec * 1000).toLong()) {
                    svc.setStatus("Atacando: ${mon.t.t.name}")
                    svc.tap(mx.toFloat(), (my + cfg.offsetPx).toFloat())
                    lastClick = now()
                }
            } else if (t0 < travelUntil) {
                svc.setStatus(if (cfg.useMap) "Caminhando até o destino…" else "Passeando…")
            } else if (t0 - lastActivity > (cfg.noMonsterSec * 1000).toLong()) {
                focusX = -9999
                focusY = -9999
                if (cfg.useMap && cfg.mapBtn != null && cfg.dests.isNotEmpty()) {
                    val mp = cfg.mapBtn!!
                    svc.setStatus("Sem monstro: abrindo o mapa…")
                    svc.tap(mp.fx * bw, mp.fy * bh)
                    nap((cfg.mapOpenSec * 1000).toLong())
                    val d = if (cfg.randomDest) cfg.dests.random() else cfg.dests[destIdx++ % cfg.dests.size]
                    svc.setStatus("Indo para outro lugar…")
                    svc.tap(d.fx * bw, d.fy * bh)
                    nap(800)
                    val cl = cfg.mapClose
                    if (cl != null) {
                        svc.tap(cl.fx * bw, cl.fy * bh)
                        nap(600)
                    }
                    travelUntil = now() + (cfg.travelSec * 1000).toLong()
                    lastActivity = travelUntil
                } else {
                    // passeio: toca no chão perto do personagem (centro da área de busca)
                    val cxr = (x0 + x1) / 2f * sc
                    val cyr = (y0 + y1) / 2f * sc
                    val r = cfg.wanderPct / 100f * bw
                    var px = cxr
                    var py = cyr
                    for (k in 0 until 6) {
                        px = (cxr + (rnd.nextFloat() * 2f - 1f) * r).coerceIn(x0.toFloat() * sc + 20f, x1.toFloat() * sc - 20f)
                        py = (cyr + (rnd.nextFloat() * 2f - 1f) * r).coerceIn(y0.toFloat() * sc + 20f, y1.toFloat() * sc - 20f)
                        if (Math.abs(px - cxr) + Math.abs(py - cyr) > bw * 0.1f) break
                    }
                    svc.setStatus("Sem monstro: passeando…")
                    svc.tap(px, py)
                    travelUntil = now() + (cfg.wanderSec * 1000).toLong()
                    lastActivity = travelUntil - (cfg.noMonsterSec * 1000).toLong() + 500L
                }
            } else {
                svc.setStatus("Rodando… procurando monstro")
            }
            nap(maxOf(100L, cfg.scanMs - (now() - t0)))
        }
    }
}
