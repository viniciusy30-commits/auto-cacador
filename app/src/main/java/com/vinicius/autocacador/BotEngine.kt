package com.vinicius.autocacador

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

class BotEngine(private val svc: BotService) {
    @Volatile var running = false
    private var th: Thread? = null

    private class T(val t: Target, val tpl: Matcher.Tpl)
    private class Found(val t: T, val hit: Matcher.Hit)

    private val sc = 2

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

    private fun best(g: Gray, list: List<T>, x0: Int, y0: Int, x1: Int, y1: Int, min: Float): Found? {
        var b: Found? = null
        for (t in list) {
            if (!running) break
            val h = Matcher.find(g, t.tpl, x0, y0, x1, y1, min) ?: continue
            if (b == null || h.score > b.hit.score) b = Found(t, h)
        }
        return b
    }

    private fun actions(bmp: Bitmap, monster: Boolean, lastUse: HashMap<String, Long>) {
        val n = now()
        for (a in Store.cfg.actions) {
            val b = a.btn ?: continue
            if (!a.enabled) continue
            if (n - (lastUse[a.id] ?: 0L) < (a.cooldownSec * 1000).toLong()) continue
            val fire: Boolean = if (a.type == ActionType.SKILL) {
                monster
            } else {
                val bar = a.bar
                if (bar == null) true else Matcher.far(
                    Matcher.avg(bmp, (bar.fx * bmp.width).toInt(), (bar.fy * bmp.height).toInt()), a.barColor
                )
            }
            if (fire) {
                svc.tap(b.fx * bmp.width, b.fy * bmp.height)
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
        val lastUse = HashMap<String, Long>()
        val ignore = ArrayList<LongArray>()
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
            val mon = best(g, mons, x0, y0, x1, y1, cfg.monScore / 100f)
            var item: Found? = if (items.isNotEmpty() && running) best(g, items, x0, y0, x1, y1, cfg.itemScore / 100f) else null
            ignore.removeAll { it[2] < t0 }
            val itf = item
            if (itf != null && ignore.any { Math.abs(it[0] - itf.hit.x * sc) < 50 && Math.abs(it[1] - itf.hit.y * sc) < 50 }) item = null
            if (item == null) clicks.clear()
            actions(bmp, mon != null, lastUse)
            bmp.recycle()
            if (!running) break

            val it2 = item
            if (it2 != null && (cfg.lootFirst || mon == null)) {
                val rx = it2.hit.x * sc
                val ry = it2.hit.y * sc
                val key = (rx / 40).toLong() * 100000L + (ry / 40)
                val c = (clicks[key] ?: 0) + 1
                clicks[key] = c
                if (c > 3) {
                    ignore.add(longArrayOf(rx.toLong(), ry.toLong(), t0 + 20000))
                    clicks.remove(key)
                } else {
                    svc.setStatus("Pegando: ${it2.t.t.name}")
                    svc.tap(rx.toFloat(), ry.toFloat())
                    lastActivity = now()
                    travelUntil = 0
                    nap(700)
                }
            } else if (mon != null) {
                travelUntil = 0
                lastActivity = t0
                if (t0 - lastClick >= (cfg.reclickSec * 1000).toLong()) {
                    svc.setStatus("Atacando: ${mon.t.t.name}")
                    svc.tap((mon.hit.x * sc).toFloat(), (mon.hit.y * sc + cfg.offsetPx).toFloat())
                    lastClick = now()
                }
            } else if (t0 < travelUntil) {
                svc.setStatus("Caminhando até o destino…")
            } else if (t0 - lastActivity > (cfg.noMonsterSec * 1000).toLong() && cfg.mapBtn != null && cfg.dests.isNotEmpty()) {
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
                svc.setStatus("Rodando… procurando monstro")
            }
            nap(maxOf(100L, cfg.scanMs - (now() - t0)))
        }
    }
}
