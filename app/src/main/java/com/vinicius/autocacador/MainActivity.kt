package com.vinicius.autocacador

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private var tab = 0
    private val tabIcons = ArrayList<IconView>()
    private val tabLabels = ArrayList<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val root = LinearLayout(this)
        root.orientation = VERT
        root.setBackgroundColor(Pal.BG)
        val head = LinearLayout(this)
        head.orientation = HORZ
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(20), dp(18), dp(20), dp(6))
        head.addView(IconView(this, IconView.TARGET, Pal.ACC), LinearLayout.LayoutParams(dp(30), dp(30)))
        val ht = text("Caçador de Vampires", 20f, Pal.TXT, true)
        ht.setPadding(dp(10), 0, 0, 0)
        head.addView(ht)
        root.addView(head)
        scroll = ScrollView(this)
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        content = LinearLayout(this)
        content.orientation = VERT
        content.setPadding(dp(16), dp(8), dp(16), dp(24))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = HORZ
        bar.setBackgroundColor(Pal.CARD)
        val names = listOf("Hunt", "Loot", "Ajustes")
        val kinds = listOf(IconView.TARGET, IconView.DROP, IconView.GEAR)
        for (i in names.indices) {
            val col = LinearLayout(this)
            col.orientation = VERT
            col.gravity = Gravity.CENTER
            col.setPadding(0, dp(8), 0, dp(8))
            val ic = IconView(this, kinds[i], Pal.SUB)
            val lb = text(names[i], 11f, Pal.SUB)
            col.addView(ic, LinearLayout.LayoutParams(dp(24), dp(24)))
            col.addView(lb)
            col.setOnClickListener { tab = i; render(true) }
            tabIcons.add(ic)
            tabLabels.add(lb)
            bar.addView(col, LinearLayout.LayoutParams(0, WC, 1f))
        }
        root.addView(bar)
        setContentView(root)
        Novidades.maybeShow(this)
        Updater.autoCheck(this)
    }

    override fun onResume() {
        super.onResume()
        render(false)
    }

    private fun render(anim: Boolean) {
        val y = if (anim) 0 else scroll.scrollY
        content.removeAllViews()
        when (tab) {
            0 -> pageHunt()
            1 -> pageLoot()
            else -> pageSettings()
        }
        if (anim) {
            content.alpha = 0f
            content.translationY = dp(14).toFloat()
            content.animate().alpha(1f).translationY(0f).setDuration(220).start()
        }
        scroll.post { scroll.scrollTo(0, y) }
        for (i in tabIcons.indices) {
            val c = if (i == tab) Pal.ACC else Pal.SUB
            tabIcons[i].color = c
            tabIcons[i].invalidate()
            tabLabels[i].setTextColor(c)
        }
    }

    // ---------- helpers de interface ----------
    private fun add(v: View, top: Int = 10) {
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.topMargin = dp(top)
        content.addView(v, lp)
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = VERT
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        c.background = rounded(Pal.CARD, dp(18).toFloat())
        return c
    }

    private fun LinearLayout.put(v: View, top: Int = 6) {
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.topMargin = dp(top)
        addView(v, lp)
    }

    private fun tip(title: String, body: String) {
        val c = LinearLayout(this)
        c.orientation = VERT
        c.setPadding(dp(16), dp(12), dp(16), dp(12))
        c.background = rounded(Pal.CARD2, dp(16).toFloat(), Pal.WARN, dp(1))
        c.put(text(title, 14f, Pal.WARN, true), 0)
        c.put(text(body, 13f, Pal.TXT), 4)
        add(c, 4)
    }

    private fun LinearLayout.status(label: String, ok: Boolean, detail: String) {
        val r = LinearLayout(context)
        r.orientation = HORZ
        r.gravity = Gravity.CENTER_VERTICAL
        val dot = View(context)
        dot.background = rounded(if (ok) Pal.OK else Pal.BAD, dp(6).toFloat())
        r.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)))
        val t = text("$label: $detail", 13f, Pal.TXT)
        t.setPadding(dp(10), 0, 0, 0)
        r.addView(t)
        put(r, 8)
    }

    private fun info(title: String, msg: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Entendi", null).show()
    }

    private fun confirm(msg: String, yes: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("Sim") { _, _ -> yes() }
            .setNegativeButton("Não", null).show()
    }

    private fun needService(): BotService? {
        val s = BotService.inst
        if (s == null) {
            info("Ative o serviço", "Vá na aba Início e ative o serviço de acessibilidade do Auto Caçador. Sem ele o app não consegue ver a tela nem tocar.")
            return null
        }
        if (Build.VERSION.SDK_INT < 30) {
            info("Android muito antigo", "Para ver a tela do jogo, o app precisa de Android 11 ou mais novo. Neste celular a detecção por imagem não funciona.")
            return null
        }
        if (s.engine.running) {
            info("Bot rodando", "Pare o bot (painel flutuante ou aba Início) antes de configurar.")
            return null
        }
        return s
    }

    private fun slider(t: String, d: String, lo: Int, hi: Int, cur: Int, fmt: (Int) -> String, set: (Int) -> Unit) {
        val c = card()
        val head = text("$t: ${fmt(cur)}", 15f, Pal.TXT, true)
        c.put(head, 0)
        c.put(text(d, 12f, Pal.SUB), 2)
        val sb = SeekBar(this)
        sb.max = hi - lo
        sb.progress = (cur - lo).coerceIn(0, hi - lo)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { head.text = "$t: ${fmt(p + lo)}" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { set((s?.progress ?: 0) + lo); Store.save() }
        })
        c.put(sb, 8)
        add(c)
    }

    private fun toggle(t: String, d: String, cur: Boolean, set: (Boolean) -> Unit) {
        val c = card()
        val r = LinearLayout(this)
        r.orientation = HORZ
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(text(t, 15f, Pal.TXT, true), LinearLayout.LayoutParams(0, WC, 1f))
        val sw = SwitchCompat(this)
        sw.isChecked = cur
        sw.setOnCheckedChangeListener { _, v -> set(v); Store.save() }
        r.addView(sw)
        c.put(r, 0)
        c.put(text(d, 12f, Pal.SUB), 4)
        add(c)
    }

    private fun fromCap(r: CapResult) = Pt(r.x / r.full.width.toFloat(), r.y / r.full.height.toFloat())

    // ---------- ESTATÍSTICAS ----------
    private fun statsCard() {
        val svc = BotService.inst ?: return
        val st = svc.engine.stats
        if (!svc.engine.running && st.kills == 0 && st.items == 0 && st.hp == 0) return
        val c = card()
        c.put(text(if (svc.engine.running) "Sessão em andamento" else "Última sessão", 16f, Pal.TXT, true), 0)
        c.put(text("⏱ ${st.elapsed()}   ⚔ ${st.kills} abates   🎒 ${st.items} itens", 14f, Pal.TXT), 6)
        c.put(text("Poções de vida: ${st.hp}   Poções de mana: ${st.mp}   Skills: ${st.skills}", 12f, Pal.SUB), 4)
        c.put(text("Abates e itens são contados pela imagem, então são aproximados. Toque na aba para atualizar.", 11f, Pal.SUB), 4)
        add(c, 10)
    }

    /** Liga o bot (e confirma o ponto de partida quando existe rota gravada). */
    private fun startHunt(svc: BotService) {
        if (Build.VERSION.SDK_INT < 30) {
            info("Android muito antigo", "Precisa de Android 11 ou mais novo.")
            return
        }
        fun go() {
            svc.showPanel()
            svc.engine.start()
            Toast.makeText(this, "Bot iniciado! Abra o jogo no Vampire Lair. Pare pelo painel flutuante.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
        val cfg = Store.cfg
        if (cfg.routeOn && cfg.route.size >= 2) {
            confirm("O personagem está EXATAMENTE no ponto onde você começou a gravar a rota? (Se não estiver, toque em Não, leve o personagem até lá e inicie de novo.)") { go() }
        } else {
            go()
        }
    }

    /** Botão que abre o teste de detecção: mostra na tela o que o bot enxerga, sem tocar em nada. */
    private fun testButton(): View = button("Testar o que o bot enxerga", Pal.ACC) {
        val s = needService()
        if (s != null) {
            s.startTest()
            moveTaskToBack(true)
        }
    }

    /** kind: 0 = monstro, 1 = item, 2 = alerta. Se "t" vier preenchido, troca a foto desse alvo. */
    private fun captureTarget(kind: Int, t: Target? = null) {
        val s = needService() ?: return
        val nm = if (t != null) "\"${t.name}\"" else "que quer capturar"
        val banner = when (kind) {
            1 -> "Deixe o item $nm caído no chão, visível na tela do jogo. Depois toque em \"Capturar agora\"."
            2 -> "Deixe o texto de alerta $nm visível na tela do jogo. Depois toque em \"Capturar agora\"."
            else -> "Deixe o monstro $nm com o NOME visível na tela do jogo. Depois toque em \"Capturar agora\"."
        }
        val pick = if (kind == 1) "Arraste um retângulo bem justo em volta do ITEM e toque em Confirmar."
        else "Arraste um retângulo bem justo em volta do NOME/TEXTO e toque em Confirmar."
        s.startCapture(banner, pick, true) { r ->
            val crop = r.crop
            if (crop != null) {
                val ok = Matcher.makeTpl(Matcher.toGray(crop, 2), kind != 1) != null
                if (!ok) {
                    s.toast("Recorte pequeno ou liso demais. Tente de novo, pegando o nome inteiro.")
                } else {
                    val id = System.currentTimeMillis().toString()
                    FileOutputStream(File(Store.tplDir(), "$id.png")).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (t != null) {
                        if (t.file.isNotBlank()) File(Store.tplDir(), t.file).delete()
                        t.file = "$id.png"
                    } else {
                        val n = Store.cfg.targets.count { kindOf(it) == kind } + 1
                        val base = when (kind) {
                            1 -> "Item "
                            2 -> "Alerta "
                            else -> "Monstro "
                        }
                        val nt = Target(id, base + n, kind == 1, "$id.png")
                        nt.alert = kind == 2
                        Store.cfg.targets.add(nt)
                    }
                    Store.save()
                }
            }
        }
        moveTaskToBack(true)
    }

    private fun setActionBtn(a: BotAction) {
        val s = needService() ?: return
        s.startCapture("Abra o jogo na tela onde aparece o botão \"${a.name}\". Depois toque em \"Capturar agora\".",
            "Toque no botão \"${a.name}\" e depois em Confirmar.", false) { r ->
            a.btn = fromCap(r)
            Store.save()
        }
        moveTaskToBack(true)
    }

    private fun setActionBar(a: BotAction) {
        val s = needService() ?: return
        s.startCapture("Deixe a barra de ${if (a.type == ActionType.HP) "vida" else "mana"} CHEIA na tela do jogo. Depois toque em \"Capturar agora\".",
            "Arraste uma linha do começo ao fim da barra (por dentro dela) e depois toque em Confirmar.", false, true) { r ->
            a.bar = fromCap(r)
            a.bar2 = Pt(r.x2 / r.full.width.toFloat(), r.y2 / r.full.height.toFloat())
            a.barColor = Matcher.avg(r.full, r.x, r.y)
            Store.save()
        }
        moveTaskToBack(true)
    }

    private fun editAction(a: BotAction) {
        val lay = LinearLayout(this)
        lay.orientation = VERT
        lay.setPadding(dp(20), dp(10), dp(20), 0)
        val n = EditText(this)
        n.setText(a.name)
        val cd = EditText(this)
        cd.setText(a.cooldownSec.toString())
        cd.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        lay.addView(text("Nome", 12f, Pal.SUB))
        lay.addView(n)
        val l = text("Recarga (segundos entre um uso e outro)", 12f, Pal.SUB)
        l.setPadding(0, dp(10), 0, 0)
        lay.addView(l)
        lay.addView(cd)
        val pc = EditText(this)
        pc.setText(a.pct.toString())
        pc.inputType = InputType.TYPE_CLASS_NUMBER
        val ur = EditText(this)
        ur.setText(a.urgentPct.toString())
        ur.inputType = InputType.TYPE_CLASS_NUMBER
        if (a.type != ActionType.SKILL) {
            val l2 = text("Beber quando a barra ficar abaixo de (%)", 12f, Pal.SUB)
            l2.setPadding(0, dp(10), 0, 0)
            lay.addView(l2)
            lay.addView(pc)
            val l3 = text("Urgente abaixo de (%): bebe quase sem esperar a recarga. 0 = desligado", 12f, Pal.SUB)
            l3.setPadding(0, dp(10), 0, 0)
            lay.addView(l3)
            lay.addView(ur)
        }
        val b = AlertDialog.Builder(this).setTitle("Editar").setView(lay)
            .setPositiveButton("Salvar") { _, _ ->
                a.name = n.text.toString().ifBlank { a.name }
                a.cooldownSec = cd.text.toString().replace(',', '.').toFloatOrNull()?.coerceIn(0.5f, 600f) ?: a.cooldownSec
                a.pct = pc.text.toString().toIntOrNull()?.coerceIn(5, 95) ?: a.pct
                a.urgentPct = ur.text.toString().toIntOrNull()?.coerceIn(0, 60) ?: a.urgentPct
                Store.save()
                render(false)
            }
            .setNegativeButton("Cancelar", null)
        if (a.bar != null) b.setNeutralButton("Remover barra") { _, _ -> a.bar = null; Store.save(); render(false) }
        b.show()
    }

    private fun kindOf(t: Target): Int = if (t.alert) 2 else if (t.isItem) 1 else 0

    /** Linha curta: bolinha de estado + nome + botão de ação. */
    private fun stepRow(c: LinearLayout, title: String, ok: Boolean, detail: String, btn: String, color: Int, onClick: () -> Unit) {
        val r = LinearLayout(this)
        r.orientation = HORZ
        r.gravity = Gravity.CENTER_VERTICAL
        val dot = View(this)
        dot.background = rounded(if (ok) Pal.OK else Pal.WARN, dp(6).toFloat())
        r.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)))
        val col = LinearLayout(this)
        col.orientation = VERT
        col.setPadding(dp(10), 0, dp(8), 0)
        col.addView(text(title, 14f, Pal.TXT, true))
        col.addView(text(detail, 11f, Pal.SUB))
        r.addView(col, LinearLayout.LayoutParams(0, WC, 1f))
        r.addView(button(btn, color, onClick), LinearLayout.LayoutParams(WC, WC))
        c.put(r, 12)
    }

    private fun slotTarget(key: String): Target? = Store.cfg.targets.firstOrNull { it.slot == key }

    private fun slotStep(c: LinearLayout, s: Vampire.Slot, optional: Boolean) {
        val t = slotTarget(s.key)
        val ok = t != null && t.ready()
        stepRow(c, s.name, ok || optional, if (ok) "capturado" else if (optional) "opcional" else "falta capturar", if (ok) "Refazer" else "Capturar", if (ok) Pal.CARD2 else Pal.ACC) {
            if (t != null) captureTarget(s.kind, t)
        }
    }

    private fun actionStep(c: LinearLayout, a: BotAction) {
        val isSkill = a.type == ActionType.SKILL
        val ok = a.btn != null && (isSkill || a.bar2 != null)
        val detail = if (!a.enabled) "desligada" else if (a.btn == null) "falta o botão" else if (!isSkill && a.bar2 == null) "falta a barra" else if (isSkill) "pronta" else "bebe abaixo de ${a.pct}%"
        val box = LinearLayout(this)
        box.orientation = VERT
        stepRow(box, a.name, ok || !a.enabled, detail, if (a.btn == null) "Botão" else "Botão", if (a.btn == null) Pal.ACC else Pal.CARD2) { setActionBtn(a) }
        if (!isSkill) {
            val r = LinearLayout(this)
            r.orientation = HORZ
            val l1 = LinearLayout.LayoutParams(0, WC, 1f)
            l1.rightMargin = dp(4)
            r.addView(button("Barra de " + (if (a.type == ActionType.HP) "vida" else "mana"), if (a.bar2 == null) Pal.ACC else Pal.CARD2) { setActionBar(a) }, l1)
            val l2 = LinearLayout.LayoutParams(0, WC, 1f)
            l2.leftMargin = dp(4)
            r.addView(button("Ajustar", Pal.CARD2) { editAction(a) }, l2)
            box.put(r, 8)
        } else {
            box.put(button("Ajustar", Pal.CARD2) { editAction(a) }, 8)
        }
        val sw = SwitchCompat(this)
        sw.isChecked = a.enabled
        sw.setOnCheckedChangeListener { _, v -> a.enabled = v; Store.save() }
        c.put(box, 4)
        val sr = LinearLayout(this)
        sr.orientation = HORZ
        sr.gravity = Gravity.CENTER_VERTICAL
        sr.addView(text("Usar no hunt", 12f, Pal.SUB), LinearLayout.LayoutParams(0, WC, 1f))
        sr.addView(sw)
        c.put(sr, 4)
    }

    // ---------- HUNT ----------
    private fun pageHunt() {
        val cfg = Store.cfg
        val svc = BotService.inst
        if (cfg.profile != "vampire") Vampire.applyProfile(cfg)
        Vampire.ensure(cfg)
        Vampire.ensureActions(cfg)

        val miss = Vampire.missing(cfg)
        val running = svc != null && svc.engine.running

        // 1) estado + botão principal
        val top = card()
        if (svc == null) {
            top.put(text("Serviço desligado", 18f, Pal.BAD, true), 0)
            top.put(text("Ligue o serviço de acessibilidade do app para ele poder ver a tela e tocar.", 12f, Pal.SUB), 4)
            top.put(button("Ligar serviço", Pal.ACC) {
                AlertDialog.Builder(this)
                    .setTitle("Como ligar")
                    .setMessage("1. Na tela que vai abrir, procure \"Auto Caçador\" (pode estar em Apps instalados / Serviços baixados).\n2. Ligue a chave e aceite o aviso.\n\nAndroid 13+ pode pedir antes: Configurações > Apps > Auto Caçador > menu (⋮) > \"Permitir configurações restritas\".")
                    .setPositiveButton("Abrir configurações") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }, 12)
        } else if (running) {
            val st = svc.engine.stats
            top.put(text("Caçando…", 18f, Pal.OK, true), 0)
            top.put(text("⏱ ${st.elapsed()}   ⚔ ${st.kills}   🎒 ${st.items}   ❤ ${st.hp} poções   ✦ ${st.skills} skills", 13f, Pal.TXT), 6)
            top.put(button("Parar bot", Pal.BAD) { svc.engine.stop(); render(false) }, 12)
        } else if (miss.isEmpty()) {
            top.put(text("Pronto para caçar", 18f, Pal.OK, true), 0)
            val st = svc.engine.stats
            if (st.kills > 0 || st.items > 0 || st.hp > 0) {
                top.put(text("Última sessão: ⏱ ${st.elapsed()}   ⚔ ${st.kills}   🎒 ${st.items}   ❤ ${st.hp}", 12f, Pal.SUB), 4)
            }
            top.put(button("Iniciar hunt", Pal.OK) { startHunt(svc) }, 12)
        } else {
            top.put(text("Falta configurar", 18f, Pal.WARN, true), 0)
            top.put(text(miss.joinToString("\n") { "• $it" }, 13f, Pal.TXT), 6)
        }
        if (svc != null && !running) {
            top.put(button("Testar o que o bot enxerga", Pal.CARD2) {
                val s = needService()
                if (s != null) {
                    s.startTest()
                    moveTaskToBack(true)
                }
            }, 8)
        }
        add(top, 4)

        // 2) configuração essencial
        val cfgCard = card()
        cfgCard.put(text("Configuração", 16f, Pal.TXT, true), 0)
        slotStep(cfgCard, Vampire.monsters[0], false)
        slotStep(cfgCard, Vampire.monsters[1], true)
        add(cfgCard, 12)

        val pot = card()
        pot.put(text("Poções e skill", 16f, Pal.TXT, true), 0)
        pot.put(text("Aponte o botão da poção e arraste uma linha na barra de vida CHEIA.", 12f, Pal.SUB), 2)
        for (a in cfg.actions) actionStep(pot, a)
        add(pot, 12)

        slotStep(card().also { add(it, 12); it.put(text("Segurança", 16f, Pal.TXT, true), 0) }, Vampire.alerts[0], true)

        routeCard()
    }

    // ---------- LOOT ----------
    private fun pageLoot() {
        val c = card()
        c.put(text("Loot", 18f, Pal.TXT, true), 0)
        c.put(text("Mate um vampire e capture cada item caído no chão (recorte justo no item). O bot só pega o que você capturou. Comece pelo Gold.", 12f, Pal.SUB), 4)
        for (s in Vampire.loot) slotStep(c, s, true)
        add(c, 4)
        val a = card()
        a.put(text("Alertas", 16f, Pal.TXT, true), 0)
        a.put(text("Se um destes textos aparecer na tela, o bot para e o celular vibra.", 12f, Pal.SUB), 2)
        for (s in Vampire.alerts) slotStep(a, s, true)
        add(a, 12)
        add(button("Testar o que o bot enxerga", Pal.ACC) {
            val sv = needService()
            if (sv != null) {
                sv.startTest()
                moveTaskToBack(true)
            }
        }, 12)
    }

    // ---------- ROTA ----------
    private fun routeCard() {
        val cfg = Store.cfg
        val n = cfg.route.size
        val c = card()
        c.put(text("Rota (opcional)", 16f, Pal.TXT, true), 0)
        c.put(text(if (n >= 2) "$n pontos gravados. Comece sempre com o personagem no ponto onde gravou." else "Sem rota: o bot passeia sozinho e troca de direção quando bate na parede.", 12f, if (n >= 2) Pal.OK else Pal.SUB), 4)
        c.put(button(if (n >= 2) "Gravar de novo" else "Gravar rota", Pal.CARD2) {
            val s = needService()
            if (s != null) {
                s.startRecording()
                moveTaskToBack(true)
            }
        }, 10)
        if (n >= 2) {
            val sw = SwitchCompat(this)
            sw.isChecked = cfg.routeOn
            sw.setOnCheckedChangeListener { _, v -> cfg.routeOn = v; Store.save() }
            c.put(text("Seguir a rota", 14f, Pal.TXT, true), 10)
            c.put(sw, 2)
            c.put(button("Apagar rota", Pal.CARD2) {
                confirm("Apagar a rota gravada?") {
                    cfg.route.clear()
                    cfg.routeOn = false
                    Store.save()
                    render(false)
                }
            }, 8)
        }
        add(c, 12)
    }

    // ---------- AJUSTES ----------
    private fun pageSettings() {
        val cfg = Store.cfg
        slider("Sensibilidade para monstros", "Se não acha o vampire, diminua. Se toca onde não deve, aumente.",
            40, 95, cfg.monScore, { "$it%" }) { cfg.monScore = it }
        slider("Sensibilidade para itens", "O mesmo, para os itens do chão.",
            40, 95, cfg.itemScore, { "$it%" }) { cfg.itemScore = it }
        slider("Toque abaixo do nome", "O nome fica acima do monstro: desce o toque para acertar o corpo.",
            0, 200, cfg.offsetPx, { "$it px" }) { cfg.offsetPx = it }
        slider("Parar se a vida ficar crítica", "Abaixo disso mesmo bebendo poção, o bot para e o celular vibra.",
            0, 40, cfg.panicPct, { if (it == 0) "desligado" else "$it%" }) { cfg.panicPct = it }
        slider("Parar se ficar sem ação", "Tempo sem ver monstro nem pegar item (travado, perdido ou desconectado).",
            0, 30, cfg.idleStopMin, { if (it == 0) "desligado" else "$it min" }) { cfg.idleStopMin = it }
        slider("Limite de tempo da sessão", "Para o bot sozinho depois deste tempo.",
            0, 240, cfg.maxRunMin, { if (it == 0) "sem limite" else "$it min" }) { cfg.maxRunMin = it }

        val r = card()
        r.put(text("Voltar ao padrão do Vampire Lair", 15f, Pal.TXT, true), 0)
        r.put(text("Refaz os tempos e ajustes. Seus nomes, itens e botões continuam salvos.", 12f, Pal.SUB), 2)
        r.put(button("Restaurar padrão", Pal.CARD2) {
            confirm("Restaurar os ajustes padrão do Vampire Lair?") {
                Vampire.applyProfile(cfg)
                Toast.makeText(this, "Padrão restaurado!", Toast.LENGTH_SHORT).show()
                render(false)
            }
        }, 10)
        add(r, 12)

        val c = card()
        c.put(text("Versão ${Updater.installedName(this)}", 14f, Pal.TXT, true), 0)
        c.put(button("Verificar atualização", Pal.CARD2) { Updater.manualCheck(this) }, 10)
        c.put(button("Ver o que mudou", Pal.CARD2) { Novidades.showLatest(this) }, 8)
        add(c, 12)

        val h = card()
        h.put(text("Ajuda", 16f, Pal.TXT, true), 0)
        h.put(text(
            "• Tela preta ao capturar: o jogo bloqueia capturas.\n" +
                "• Não acha o vampire: refaça a captura do nome (recorte justo) ou diminua a sensibilidade.\n" +
                "• Toca no lugar errado: ajuste o \"Toque abaixo do nome\".\n" +
                "• O serviço desliga sozinho? Tire o app da economia de bateria.\n" +
                "• Bots podem causar banimento no jogo. Use por sua conta e risco.", 12f, Pal.SUB), 8)
        add(h, 12)
    }
}
