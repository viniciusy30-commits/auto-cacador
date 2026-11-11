package com.vinicius.autocacador

import android.graphics.Bitmap
import java.util.Random

/** Números da sessão atual (os de abates e itens são aproximados). */
class Stats {
    @Volatile var kills = 0
    @Volatile var items = 0
    @Volatile var hp = 0
    @Volatile var mp = 0
    @Volatile var skills = 0
    val startMs: Long = System.currentTimeMillis()

    fun short(): String = "⚔ $kills · 🎒 $items"

    fun elapsed(): String {
        val s = (System.currentTimeMillis() - startMs) / 1000
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }
}

class BotEngine(private val svc: BotService) {
    @Volatile var running = false
    @Volatile var stats = Stats()
    @Volatile private var stopMsg: String? = null
    private var th: Thread? = null

    private val sc = Vision.SC
    private val rnd = Random()

    // 8 direções para explorar: N, NE, L, SE, S, SO, O, NO
    private val dirX = floatArrayOf(0f, 0.7f, 1f, 0.7f, 0f, -0.7f, -1f, -0.7f)
    private val dirY = floatArrayOf(-1f, -0.7f, 0f, 0.7f, 1f, 0.7f, 0f, -0.7f)

    fun start() {
        if (running) return
        running = true
        stopMsg = null
        stats = Stats()
        svc.runState(true)
        th = Thread {
            try {
                loop()
                svc.setStatus(stopMsg ?: ("Parado · " + stats.short()))
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

    /** Para o bot por segurança, avisa na tela e faz o celular vibrar. */
    private fun stopWith(msg: String) {
        stopMsg = msg
        running = false
        svc.alert(msg)
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun nap(ms: Long) {
        var left = ms
        while (running && left > 0) {
            val s = minOf(left, 100L)
            Thread.sleep(s)
            left -= s
        }
    }

    /** Pequena variação (px) para os toques no mundo. */
    private fun jit(): Float = (rnd.nextFloat() * 2f - 1f) * Store.cfg.tapJitter

    /** Assinatura barata da cena (grade de pontos) para saber se a tela mexeu. */
    private fun sig(g: Gray, x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        val cols = 24
        val rows = 14
        val out = IntArray(cols * rows)
        val w = maxOf(1, x1 - x0 - 1)
        val h = maxOf(1, y1 - y0 - 1)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = (x0 + w * c / (cols - 1)).coerceIn(0, g.w - 1)
                val y = (y0 + h * r / (rows - 1)).coerceIn(0, g.h - 1)
                out[r * cols + c] = g.d[y * g.w + x].toInt() and 255
            }
        }
        return out
    }

    private fun movedRatio(a: IntArray, b: IntArray): Float {
        var n = 0
        for (i in a.indices) if (Math.abs(a[i] - b[i]) > 24) n++
        return n.toFloat() / a.size
    }

    private fun pickHeading(t: Long, blocked: LongArray, cur: Int): Int {
        val ok = ArrayList<Int>()
        for (i in 0 until 8) if (i != cur && blocked[i] <= t) ok.add(i)
        if (ok.isEmpty()) {
            for (i in 0 until 8) blocked[i] = 0L
            return (cur + 1 + rnd.nextInt(7)) % 8
        }
        return ok[rnd.nextInt(ok.size)]
    }

    private class Vit(var hp: Int, var urgent: Boolean)

    /**
     * Poções de vida e mana. Roda ANTES de procurar monstros (a procura demora e a vida não espera).
     * Devolve a menor vida medida (0..100, ou -1 se não há barra) e se está em estado urgente.
     */
    private fun vitals(bmp: Bitmap, lastUse: HashMap<String, Long>): Vit {
        val v = Vit(-1, false)
        val bw = bmp.width
        val bh = bmp.height
        val list = Store.cfg.actions.filter { it.type != ActionType.SKILL }.sortedBy { it.type.ordinal }
        for (a in list) {
            val b = a.btn ?: continue
            if (!a.enabled) continue
            val bar = a.bar
            val b2 = a.bar2
            var level = -1
            val fire: Boolean
            if (bar == null) {
                fire = true
            } else if (b2 != null) {
                level = Matcher.fillPct(bmp, (bar.fx * bw).toInt(), (bar.fy * bh).toInt(), (b2.fx * bw).toInt(), (b2.fy * bh).toInt(), a.barColor)
                fire = level < a.pct
            } else {
                fire = Matcher.far(Matcher.avg(bmp, (bar.fx * bw).toInt(), (bar.fy * bh).toInt()), a.barColor)
            }
            if (a.type == ActionType.HP && level >= 0) {
                if (v.hp < 0 || level < v.hp) v.hp = level
                if (a.urgentPct > 0 && level < a.urgentPct) v.urgent = true
            }
            if (!fire) continue
            var cd = (a.cooldownSec * 1000).toLong()
            if (a.urgentPct > 0 && level in 0 until a.urgentPct) cd = minOf(cd, 900L)
            if (now() - (lastUse[a.id] ?: 0L) < cd) continue
            svc.tap(b.fx * bw, b.fy * bh)
            lastUse[a.id] = now()
            if (a.type == ActionType.HP) stats.hp++ else stats.mp++
            nap(150)
        }
        return v
    }

    /** Skills: só enquanto há monstro na tela, respeitando a recarga de cada uma. */
    private fun skills(bw: Int, bh: Int, lastUse: HashMap<String, Long>) {
        for (a in Store.cfg.actions) {
            if (a.type != ActionType.SKILL || !a.enabled) continue
            val b = a.btn ?: continue
            if (now() - (lastUse[a.id] ?: 0L) < (a.cooldownSec * 1000).toLong()) continue
            svc.tap(b.fx * bw, b.fy * bh)
            lastUse[a.id] = now()
            stats.skills++
            nap(150)
        }
    }

    private fun loop() {
        val cfg = Store.cfg
        val mons = Vision.load(0)
        val items = Vision.load(1)
        val alerts = Vision.load(2)
        if (mons.isEmpty()) {
            svc.setStatus("Nenhum monstro válido. Capture de novo na aba Alvos.")
            return
        }
        val hpAct = cfg.actions.firstOrNull { it.type == ActionType.HP && it.enabled && it.btn != null }
        if (hpAct == null) svc.toast("Atenção: sem poção de vida configurada. Caçar assim é arriscado.")
        else if (hpAct.bar2 == null) svc.toast("Atenção: a poção de vida está sem barra. Ela vai beber por tempo, não pela vida real.")
        svc.setStatus("Rodando… procurando monstro")

        val startMs = now()
        var lastProgress = startMs
        var lastActivity = startMs
        var lastClick = 0L
        var travelUntil = 0L
        var destIdx = 0
        var fails = 0
        var scanN = 0
        var urgentSkips = 0
        var focusX = -9999
        var focusY = -9999
        var focusSince = 0L
        var prevMx = -9999
        var prevMy = -9999
        val lastUse = HashMap<String, Long>()
        val monIgn = ArrayList<LongArray>()
        val itemIgn = ArrayList<LongArray>()
        val clicks = HashMap<Long, Int>()

        // abates
        var inFight = false
        var fightStart = 0L
        var missScans = 0
        var lastMonSeen = 0L
        // loot
        var pendAt = 0L
        var pendT: Target? = null
        var lootStart = 0L
        // anti-travamento / exploração
        var prevSig: IntArray? = null
        var lastMoveAt = now()
        var heading = rnd.nextInt(8)
        val blockedUntil = LongArray(8)
        var lastExploreTap = 0L
        var exploreTaps = 0
        var forceExplore = false
        // emergência
        var critSince = 0L
        // rota gravada: segue os pontos medindo o quanto o cenário rola (odometria visual)
        val route = cfg.route
        var routeActive = cfg.routeOn && route.size >= 2
        val odo = Odo()
        if (routeActive) odo.reset(route[0].x, route[0].y)
        var ri = 1
        var rdir = 1
        var holdUntil = 0L
        var routeTapAt = 0L
        var lastRDist = 9f
        var noProg = 0
        var skips = 0

        fun nextWp() {
            noProg = 0
            lastRDist = 9f
            routeTapAt = 0L
            if (cfg.routePing) {
                ri += rdir
                if (ri >= route.size) {
                    rdir = -1
                    ri = route.size - 2
                } else if (ri < 0) {
                    rdir = 1
                    ri = 1
                }
            } else {
                ri = (ri + 1) % route.size
            }
        }

        while (running) {
            val t0 = now()
            if (cfg.maxRunMin > 0 && t0 - startMs > cfg.maxRunMin * 60000L) {
                stopWith("Tempo limite da sessão atingido. Bot parado.")
                break
            }
            if (cfg.idleStopMin > 0 && t0 - lastProgress > cfg.idleStopMin * 60000L) {
                stopWith("⚠ ${cfg.idleStopMin} min sem ver monstro nem pegar item. Bot parado (travado, perdido ou desconectado?).")
                break
            }
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
            scanN++
            val bw = bmp.width
            val bh = bmp.height

            // 1) vida e mana primeiro: a procura de monstros demora e a vida não espera
            val vit = vitals(bmp, lastUse)
            val g = Matcher.toGray(bmp, sc)
            bmp.recycle()
            if (!running) break

            // vida crítica mesmo bebendo poção: para e avisa
            if (cfg.panicPct > 0 && vit.hp >= 0) {
                if (vit.hp < cfg.panicPct) {
                    if (critSince == 0L) {
                        critSince = t0
                    } else if (t0 - critSince > cfg.panicSec * 1000L) {
                        stopWith("⚠ Vida crítica (${vit.hp}%)! Bot parado.")
                        break
                    }
                } else {
                    critSince = 0L
                }
            }
            // vida muito baixa: volta a olhar a vida logo, sem perder tempo procurando monstro
            if (vit.urgent && urgentSkips < 6) {
                urgentSkips++
                svc.setStatus("Vida baixa (${vit.hp}%): bebendo poção…")
                nap(120)
                continue
            }
            urgentSkips = 0

            val x0 = g.w * cfg.mLeft / 100
            val x1 = g.w - g.w * cfg.mRight / 100
            val y0 = g.h * cfg.mTop / 100
            val y1 = g.h - g.h * cfg.mBottom / 100

            // a tela mexeu desde a última olhada?
            val sg = sig(g, x0, y0, x1, y1)
            val ps = prevSig
            if (ps == null || movedRatio(ps, sg) > 0.10f) lastMoveAt = t0
            prevSig = sg

            if (routeActive) {
                val rg = odoRegion(g, cfg)
                if (!odo.update(g, rg[0], rg[1], rg[2], rg[3]) && odo.lost >= 8) {
                    // não dá para saber onde o personagem está: larga a rota e explora sozinho
                    routeActive = false
                    forceExplore = true
                    svc.toast("Perdi a posição na rota (chão liso demais). Vou explorar sozinho.")
                }
            }

            // alertas (boss, tela de morte...) em tela cheia, a cada 2 olhadas
            if (alerts.isNotEmpty() && scanN % 2 == 1) {
                val al = Vision.best(g, alerts, 0, 0, g.w, g.h, cfg.monScore / 100f, false, { running }) { _, _ -> false }
                if (al != null) {
                    stopWith("⚠ ${al.t.t.name} na tela! Bot parado.")
                    break
                }
            }

            monIgn.removeAll { it[2] < t0 }
            itemIgn.removeAll { it[2] < t0 }
            val mon = Vision.best(g, mons, x0, y0, x1, y1, cfg.monScore / 100f, cfg.nearest, { running }) { x, y ->
                monIgn.any { Math.abs(it[0] - x) < 70 && Math.abs(it[1] - y) < 70 }
            }
            val item: Vision.Found? = if (items.isNotEmpty() && running) {
                Vision.best(g, items, x0, y0, x1, y1, cfg.itemScore / 100f, true, { running }) { x, y ->
                    itemIgn.any { Math.abs(it[0] - x) < 50 && Math.abs(it[1] - y) < 50 }
                }
            } else null
            if (item == null) {
                clicks.clear()
                lootStart = 0L
            }
            if (!running) break

            // skills enquanto há monstro
            if (mon != null) skills(bw, bh, lastUse)
            if (!running) break

            // contagem de abates (aproximada)
            if (mon != null) {
                lastMonSeen = t0
                lastProgress = t0
                missScans = 0
                if (!inFight) {
                    inFight = true
                    fightStart = t0
                }
            } else if (inFight) {
                missScans++
                if (missScans >= 2) {
                    if (lastMonSeen - fightStart >= 1500L) stats.kills++
                    inFight = false
                }
            }

            // contagem de itens pegos (aproximada): o item tocado sumiu da tela
            if (pendAt != 0L && t0 - pendAt > 600L) {
                if (item == null || item.t.t !== pendT) {
                    stats.items++
                    lastProgress = t0
                    pendAt = 0L
                    pendT = null
                }
            }

            // item perto do centro (personagem)? dá para pegar mesmo lutando
            var wantLoot = false
            if (item != null) {
                if (mon == null || cfg.lootFirst) {
                    wantLoot = true
                } else if (cfg.lootNearPct > 0 && (vit.hp < 0 || vit.hp >= 50)) {
                    val dx = (item.hit.x - (x0 + x1) / 2).toDouble()
                    val dy = (item.hit.y - (y0 + y1) / 2).toDouble()
                    wantLoot = Math.hypot(dx, dy) < cfg.lootNearPct / 100.0 * g.w
                }
            }

            if (item != null && wantLoot) {
                val rx = item.hit.x * sc
                val ry = item.hit.y * sc
                val key = (rx / 40).toLong() * 100000L + (ry / 40)
                val c = (clicks[key] ?: 0) + 1
                clicks[key] = c
                if (lootStart == 0L) lootStart = t0
                if (c > cfg.lootTries || t0 - lootStart > 15000L) {
                    // não consegue pegar (atrás de parede, por exemplo): ignora por um tempo
                    itemIgn.add(longArrayOf(rx.toLong(), ry.toLong(), t0 + 20000))
                    clicks.remove(key)
                    lootStart = 0L
                    pendAt = 0L
                    pendT = null
                } else {
                    svc.setStatus("Pegando: ${item.t.t.name} · ${stats.short()}")
                    svc.tap(rx + jit() / 2f, ry + jit() / 2f)
                    pendAt = now()
                    pendT = item.t.t
                    lastActivity = now()
                    travelUntil = 0
                    nap(800)
                }
            } else if (mon != null) {
                travelUntil = 0
                lastActivity = t0
                val mx = mon.hit.x * sc
                val my = mon.hit.y * sc
                // o monstro se mexeu? (corre atrás de quem foge)
                val moving = Math.abs(mx - prevMx) > 30 || Math.abs(my - prevMy) > 30
                prevMx = mx
                prevMy = my
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
                val gap = if (moving) minOf(cfg.chaseSec, cfg.reclickSec) else cfg.reclickSec
                if (t0 - lastClick >= (gap * 1000).toLong()) {
                    svc.setStatus((if (moving) "Perseguindo: " else "Atacando: ") + mon.t.t.name + " · " + stats.short())
                    svc.tap(mx + jit(), my + cfg.offsetPx + jit())
                    lastClick = now()
                }
            } else if (items.isNotEmpty() && cfg.lootSweepSec > 0f && lastMonSeen > 0L &&
                t0 - lastMonSeen < (cfg.lootSweepSec * 1000).toLong()
            ) {
                // acabou de matar: espera o loot aparecer e varre o chão antes de sair andando
                svc.setStatus("Varrendo o chão atrás de loot… · ${stats.short()}")
            } else if (routeActive) {
                val tgt = route[ri]
                val vx = tgt.x - odo.fx
                val vy = tgt.y - odo.fy
                val dist = Math.hypot(vx.toDouble(), vy.toDouble()).toFloat()
                if (holdUntil > 0L) {
                    if (t0 < holdUntil) {
                        svc.setStatus("Ponto de caça: esperando monstros… · ${stats.short()}")
                    } else {
                        holdUntil = 0L
                        nextWp()
                    }
                } else if (dist < 0.02f) {
                    if (tgt.hold && cfg.routeHoldSec > 0) {
                        holdUntil = t0 + cfg.routeHoldSec * 1000L
                    } else {
                        skips = 0
                        nextWp()
                    }
                } else {
                    val moving = t0 - lastMoveAt < 700L
                    val due = if (moving) t0 - routeTapAt > 2500L else t0 - routeTapAt > 900L
                    if (due) {
                        if (routeTapAt > 0L && lastRDist - dist < 0.004f) {
                            noProg++
                        } else {
                            noProg = 0
                            skips = 0
                        }
                        lastRDist = dist
                        if (noProg >= 3) {
                            // não anda mais (parede, obstáculo): pula este ponto
                            skips++
                            if (skips >= 4) {
                                // rota bloqueada: em vez de parar, explora sozinho
                                routeActive = false
                                forceExplore = true
                                svc.toast("Rota bloqueada. Vou explorar sozinho.")
                            } else {
                                svc.setStatus("Caminho bloqueado, pulando ponto…")
                                nextWp()
                            }
                        } else {
                            // toca no chão na direção do ponto: a distância do toque é a distância real no mapa
                            var f = minOf(1f, cfg.wanderPct / 100f / dist)
                            val hx = bw / 2f - 40f
                            val hy = bh / 2f - 40f
                            if (Math.abs(vx) * bw * f > hx) f = hx / (Math.abs(vx) * bw)
                            if (Math.abs(vy) * bw * f > hy) f = hy / (Math.abs(vy) * bw)
                            svc.setStatus("Seguindo a rota: ponto ${ri + 1}/${route.size} · ${stats.short()}")
                            svc.tap(bw / 2f + vx * bw * f, bh / 2f + vy * bw * f)
                            routeTapAt = now()
                        }
                    } else {
                        svc.setStatus("Seguindo a rota: ponto ${ri + 1}/${route.size} · ${stats.short()}")
                    }
                }
            } else if (t0 < travelUntil) {
                if (cfg.useMap) {
                    svc.setStatus("Caminhando até o destino…")
                } else {
                    svc.setStatus("Explorando… · ${stats.short()}")
                    // tocou no chão mas a tela não andou: parede na frente, troca de direção
                    if ((cfg.explore || forceExplore) && lastExploreTap > 0L && t0 - lastExploreTap > 1200L && lastMoveAt < lastExploreTap) {
                        blockedUntil[heading] = t0 + 25000
                        heading = pickHeading(t0, blockedUntil, heading)
                        lastExploreTap = 0L
                        travelUntil = 0L
                        lastActivity = t0 - (cfg.noMonsterSec * 1000).toLong() - 1L
                        svc.setStatus("Bati na parede, mudando de direção…")
                    }
                }
            } else if (t0 - lastActivity > (cfg.noMonsterSec * 1000).toLong()) {
                focusX = -9999
                focusY = -9999
                prevMx = -9999
                prevMy = -9999
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
                    val cxr = (x0 + x1) / 2f * sc
                    val cyr = (y0 + y1) / 2f * sc
                    val r = cfg.wanderPct / 100f * bw
                    var px = cxr
                    var py = cyr
                    if (cfg.explore || forceExplore) {
                        // vai em linha reta; depois de várias andadas sem achar nada, troca de direção
                        exploreTaps++
                        if (exploreTaps > 8) {
                            exploreTaps = 0
                            heading = pickHeading(t0, blockedUntil, heading)
                        }
                        if (blockedUntil[heading] > t0) heading = pickHeading(t0, blockedUntil, heading)
                        px = (cxr + dirX[heading] * r).coerceIn(x0.toFloat() * sc + 20f, x1.toFloat() * sc - 20f)
                        py = (cyr + dirY[heading] * r).coerceIn(y0.toFloat() * sc + 20f, y1.toFloat() * sc - 20f)
                    } else {
                        for (k in 0 until 6) {
                            px = (cxr + (rnd.nextFloat() * 2f - 1f) * r).coerceIn(x0.toFloat() * sc + 20f, x1.toFloat() * sc - 20f)
                            py = (cyr + (rnd.nextFloat() * 2f - 1f) * r).coerceIn(y0.toFloat() * sc + 20f, y1.toFloat() * sc - 20f)
                            if (Math.abs(px - cxr) + Math.abs(py - cyr) > bw * 0.1f) break
                        }
                    }
                    svc.setStatus("Sem monstro: explorando… · ${stats.short()}")
                    svc.tap(px, py)
                    lastExploreTap = now()
                    travelUntil = now() + (cfg.wanderSec * 1000).toLong()
                    lastActivity = travelUntil - (cfg.noMonsterSec * 1000).toLong() + 500L
                }
            } else {
                svc.setStatus("Rodando… procurando monstro · ${stats.short()}")
            }
            nap(maxOf(100L, cfg.scanMs - (now() - t0)))
        }
    }
}
