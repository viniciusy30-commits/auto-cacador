package com.vinicius.autocacador

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Tudo que "enxerga" a tela: usado pelo bot e pelo teste de detecção. */
object Vision {
    /** A imagem é reduzida pela metade antes de comparar (mais rápido). */
    const val SC = 2

    class T(val t: Target, val tpl: Matcher.Tpl)
    class Found(val t: T, val hit: Matcher.Hit)

    /** Retângulo achado, em pixels da tela inteira. kind: 0 = monstro, 1 = item, 2 = alerta. */
    class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val kind: Int, val label: String)
    class Report(val boxes: List<Box>, val lines: List<String>)

    fun kindOf(t: Target): Int = if (t.alert) 2 else if (t.isItem) 1 else 0

    /** kind: 0 = monstros, 1 = itens, 2 = alertas. Só carrega alvos ligados e com foto. */
    fun load(kind: Int): List<T> {
        val out = ArrayList<T>()
        for (t in Store.cfg.targets) {
            if (kindOf(t) != kind || !t.enabled || !t.ready()) continue
            val b = BitmapFactory.decodeFile(File(Store.tplDir(), t.file).path) ?: continue
            val g = Matcher.toGray(b, SC)
            b.recycle()
            val tpl = Matcher.makeTpl(g, kind != 1) ?: continue
            out.add(T(t, tpl))
        }
        return out
    }

    /** Escolhe o acerto mais perto do centro (nearest) ou o mais parecido com a foto. */
    fun best(
        g: Gray, list: List<T>, x0: Int, y0: Int, x1: Int, y1: Int, min: Float,
        nearest: Boolean, alive: () -> Boolean, skip: (Int, Int) -> Boolean
    ): Found? {
        val cx = (x0 + x1) / 2
        val cy = (y0 + y1) / 2
        var b: Found? = null
        var bd = Long.MAX_VALUE
        for (t in list) {
            if (!alive()) break
            for (h in Matcher.findAll(g, t.tpl, x0, y0, x1, y1, min)) {
                if (skip(h.x * SC, h.y * SC)) continue
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

    /** Área de busca (em pixels da imagem reduzida): [x0, y0, x1, y1], já sem as margens ignoradas. */
    fun area(g: Gray, cfg: Cfg): IntArray = intArrayOf(
        g.w * cfg.mLeft / 100,
        g.h * cfg.mTop / 100,
        g.w - g.w * cfg.mRight / 100,
        g.h - g.h * cfg.mBottom / 100
    )

    /** Olha uma imagem da tela e diz o que o bot enxergaria (sem tocar em nada). */
    fun report(bmp: Bitmap): Report {
        val cfg = Store.cfg
        val g = Matcher.toGray(bmp, SC)
        val ar = area(g, cfg)
        val boxes = ArrayList<Box>()
        val lines = ArrayList<String>()
        val names = arrayOf("Monstros", "Itens", "Alertas")
        for (kind in 0..2) {
            val list = load(kind)
            val min = (if (kind == 1) cfg.itemScore else cfg.monScore) / 100f
            var cnt = 0
            val found = ArrayList<String>()
            for (t in list) {
                val hits = if (kind == 2) Matcher.findAll(g, t.tpl, 0, 0, g.w, g.h, min)
                else Matcher.findAll(g, t.tpl, ar[0], ar[1], ar[2], ar[3], min)
                for (h in hits) {
                    val pct = (h.score * 100).toInt()
                    boxes.add(
                        Box(
                            (h.x - t.tpl.w / 2) * SC, (h.y - t.tpl.h / 2) * SC,
                            (h.x + t.tpl.w / 2) * SC, (h.y + t.tpl.h / 2) * SC,
                            kind, t.t.name + " " + pct + "%"
                        )
                    )
                    cnt++
                }
                if (hits.isNotEmpty()) found.add(t.t.name + " x" + hits.size)
            }
            if (list.isEmpty()) {
                lines.add(names[kind] + ": nenhum cadastrado com foto")
            } else {
                lines.add(names[kind] + ": " + (if (cnt == 0) "nada na tela agora" else found.joinToString(", ")))
            }
        }
        for (a in Store.cfg.actions) {
            if (!a.enabled || a.type == ActionType.SKILL) continue
            val b1 = a.bar
            val b2 = a.bar2
            if (b1 == null || b2 == null) continue
            val lv = Matcher.fillPct(
                bmp, (b1.fx * bmp.width).toInt(), (b1.fy * bmp.height).toInt(),
                (b2.fx * bmp.width).toInt(), (b2.fy * bmp.height).toInt(), a.barColor
            )
            lines.add(a.name + ": barra em " + lv + "% (bebe abaixo de " + a.pct + "%)")
        }
        return Report(boxes, lines)
    }
}
