package com.vinicius.autocacador

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
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
        val ht = text("Auto Caçador", 20f, Pal.TXT, true)
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
        val names = listOf("Início", "Hunt", "Alvos", "Ações", "Mapa", "Ajustes")
        val kinds = listOf(IconView.HOME, IconView.DROP, IconView.TARGET, IconView.FLASK, IconView.MAP, IconView.GEAR)
        for (i in names.indices) {
            val col = LinearLayout(this)
            col.orientation = VERT
            col.gravity = Gravity.CENTER
            col.setPadding(0, dp(8), 0, dp(8))
            val ic = IconView(this, kinds[i], Pal.SUB)
            val lb = text(names[i], 10f, Pal.SUB)
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
            0 -> pageHome()
            1 -> pageHunt()
            2 -> pageTargets()
            3 -> pageActions()
            4 -> pageMap()
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

    // ---------- INÍCIO ----------
    private fun pageHome() {
        val svc = BotService.inst
        val cfg = Store.cfg
        val hero = card()
        hero.put(text("Bem-vindo!", 22f, Pal.TXT, true), 0)
        hero.put(text("Este app olha a tela do seu jogo, acha o monstro pelo nome, ataca, usa poções e skills, troca de lugar pelo mapa quando não tem monstro e pega os itens do chão. Tudo é configurado aqui, passo a passo.", 14f, Pal.SUB), 6)
        add(hero, 4)

        if (cfg.profile == "vampire") {
            val pc = card()
            pc.put(text("Perfil ativo: Hunt de Vampire", 15f, Pal.OK, true), 0)
            pc.put(text("Siga o passo a passo na aba Hunt.", 12f, Pal.SUB), 2)
            add(pc, 10)
        }

        val nm = cfg.targets.count { !it.isItem && !it.alert && it.enabled && it.ready() }
        val ni = cfg.targets.count { it.isItem && it.enabled && it.ready() }
        val mapOk = cfg.mapBtn != null && cfg.dests.isNotEmpty()
        val st = card()
        st.put(text("Situação", 16f, Pal.TXT, true), 0)
        st.status("Serviço de acessibilidade", svc != null, if (svc != null) "ligado" else "desligado")
        st.status("Ver a tela (Android 11+)", Build.VERSION.SDK_INT >= 30, if (Build.VERSION.SDK_INT >= 30) "ok" else "indisponível")
        st.status("Monstros cadastrados", nm > 0, "$nm")
        st.status("Itens cadastrados (opcional)", ni > 0, "$ni")
        st.status("Poções/skills com botão (opcional)", cfg.actions.any { it.btn != null }, "${cfg.actions.count { it.btn != null }}")
        st.status("Mapa configurado (opcional)", mapOk, if (mapOk) "sim" else "não")
        add(st)
        statsCard()

        if (svc == null) {
            add(button("Ativar serviço de acessibilidade") {
                AlertDialog.Builder(this)
                    .setTitle("Como ativar")
                    .setMessage("1. Na tela que vai abrir, procure \"Auto Caçador\" (pode estar em Apps instalados / Serviços baixados).\n2. Ligue a chave e aceite o aviso.\n\nSe o Android disser que a configuração está restrita (Android 13+): Configurações > Apps > Auto Caçador > menu (⋮) > \"Permitir configurações restritas\", e tente de novo.")
                    .setPositiveButton("Abrir configurações") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }, 14)
        } else {
            add(button("Mostrar painel flutuante", Pal.CARD2) { svc.showPanel() }, 14)
            if (svc.engine.running) {
                add(button("Parar bot", Pal.BAD) { svc.engine.stop(); render(false) }, 10)
            } else {
                add(button("Iniciar bot", Pal.OK) {
                    if (nm == 0) {
                        info("Falta o monstro", "Na aba Alvos, capture pelo menos o nome de 1 monstro.")
                    } else if (Build.VERSION.SDK_INT < 30) {
                        info("Android muito antigo", "Precisa de Android 11 ou mais novo.")
                    } else {
                        svc.showPanel()
                        svc.engine.start()
                        Toast.makeText(this, "Bot iniciado! Abra o jogo. Pare pelo painel flutuante.", Toast.LENGTH_LONG).show()
                        moveTaskToBack(true)
                    }
                }, 10)
            }
        }

        val rc = card()
        rc.put(text("Dicas para o Rucoy Online", 16f, Pal.TXT, true), 0)
        rc.put(text("• Os botões da esquerda do jogo são as poções e habilidades: defina cada um na aba Ações.\n• Para atacar basta tocar no monstro; para pegar item, tocar nele; para andar, tocar no chão. É isso que o app faz.\n• O personagem fica no centro da tela, por isso o app ataca o monstro mais perto do centro.\n• Em Ajustes há um botão com valores iniciais do Rucoy.\n• Bots costumam ser proibidos pelas regras de jogos online e podem causar banimento. Use por sua conta e risco.", 12f, Pal.SUB), 8)
        add(rc)

        val g = card()
        g.put(text("Passo a passo", 16f, Pal.TXT, true), 0)
        g.put(text(
            "1. Ative o serviço de acessibilidade (botão acima).\n\n" +
                "2. Alvos > \"Capturar nome de monstro\": abra o jogo com o monstro na tela, toque em \"Capturar agora\" e arraste um retângulo bem justo em volta do NOME dele. Capture um nome para cada tipo de monstro.\n\n" +
                "3. (Opcional) Alvos > \"Capturar item\": faça o mesmo com a imagem do item caído no chão.\n\n" +
                "4. Ações: adicione poção de vida, de mana e skills. Defina onde fica cada botão.\n\n" +
                "5. Mapa: sem monstros o app passeia sozinho tocando no chão. Se preferir, ligue o uso do mapa e marque o botão e os destinos.\n\n" +
                "6. Ajustes: regule a sensibilidade e os tempos. Cada opção tem uma explicação.\n\n" +
                "7. Abra o jogo e toque em ▶ no painel flutuante. Para parar, toque em ■.\n\n" +
                "Importante: use o app só onde o jogo permitir automação. Se a captura vier preta, o jogo bloqueia capturas de tela e este método não funciona nele.", 13f, Pal.SUB), 8)
        add(g)
    }

    // ---------- ROTA GRAVADA ----------
    private fun routeCard() {
        val cfg = Store.cfg
        val n = cfg.route.size
        val holds = cfg.route.count { it.hold }
        val c = card()
        c.put(text("Rota gravada", 16f, Pal.TXT, true), 0)
        c.put(text(if (n >= 2) "$n pontos, $holds de caça. A rota começa no lugar onde você a gravou: inicie o bot com o personagem nesse ponto." else "Nenhuma rota gravada ainda.", 13f, if (n >= 2) Pal.OK else Pal.SUB), 4)
        c.put(text("Como gravar:\n1. Leve o personagem ao ponto de partida (por exemplo, a entrada do Vampire Lair).\n2. Toque em Gravar rota e volte ao jogo.\n3. Ande pelo caminho que o bot deve fazer. Toque em 📍 nos lugares onde ele deve parar e esperar monstros.\n4. Toque em Salvar.\n\nO app mede o quanto o cenário rola enquanto você anda, então a rota não depende de coordenadas do jogo (o Rucoy não mostra nenhuma). Se o bot sair do caminho ou perder a posição, ele para e avisa: leve o personagem ao ponto inicial e comece de novo.", 12f, Pal.SUB), 6)
        c.put(button(if (n >= 2) "Gravar de novo" else "Gravar rota", Pal.ACC) {
            val s = needService()
            if (s != null) {
                s.startRecording()
                moveTaskToBack(true)
            }
        }, 10)
        if (n >= 2) {
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
        toggle("Seguir a rota", "Ligado: quando não há monstro nem item, o bot anda pela rota gravada. Se um monstro aparecer, ele luta e depois volta para o caminho.", cfg.routeOn && n >= 2) { cfg.routeOn = it }
        slider("Espera nos pontos de caça", "Quanto tempo o bot fica parado em cada ponto marcado com 📍, esperando monstros.",
            0, 60, cfg.routeHoldSec, { if (it == 0) "não espera" else "$it s" }) { cfg.routeHoldSec = it }
        toggle("Ir e voltar", "Ligado: ao chegar no fim da rota, volta pelo mesmo caminho. Desligado: volta em linha reta para o começo (pode atravessar paredes).", cfg.routePing) { cfg.routePing = it }
    }

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

    // ---------- HUNT (VAMPIRE) ----------
    private fun slotRow(c: LinearLayout, s: Vampire.Slot) {
        val t = Store.cfg.targets.firstOrNull { it.slot == s.key }
        val ok = t != null && t.ready()
        val r = LinearLayout(this)
        r.orientation = HORZ
        r.gravity = Gravity.CENTER_VERTICAL
        val dot = View(this)
        dot.background = rounded(if (ok) Pal.OK else Pal.WARN, dp(6).toFloat())
        r.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)))
        val tv = text(s.name, 14f, Pal.TXT, true)
        tv.setPadding(dp(10), 0, dp(8), 0)
        r.addView(tv, LinearLayout.LayoutParams(0, WC, 1f))
        r.addView(button(if (ok) "Refazer" else "Capturar", if (ok) Pal.CARD2 else Pal.ACC) {
            if (t != null) captureTarget(s.kind, t)
        }, LinearLayout.LayoutParams(WC, WC))
        c.put(r, 10)
        c.put(text(s.hint, 11f, Pal.SUB), 2)
    }

    private fun slotCard(title: String, desc: String, slots: List<Vampire.Slot>) {
        val c = card()
        c.put(text(title, 16f, Pal.TXT, true), 0)
        c.put(text(desc, 12f, Pal.SUB), 2)
        for (s in slots) slotRow(c, s)
        add(c, 12)
    }

    private fun pageHunt() {
        val cfg = Store.cfg
        val svc = BotService.inst
        val active = cfg.profile == "vampire"
        if (active) Vampire.ensure(cfg)

        val hero = card()
        hero.put(text("Hunt de Vampire", 22f, Pal.TXT, true), 0)
        hero.put(text("Vampire Lair: Vampire (Lv 100, 450 de vida) e Red Vampire (Lv 110, 530 de vida). Os dois FOGEM quando a vida deles fica vermelha, por isso este perfil persegue mais rápido, bebe poção mais cedo, varre o chão depois de cada abate e anda em linha reta trocando de direção quando bate na parede.\n\nDrops: Gold, Greater Health e Mana Potion, Life Pendant e peças do Soldier Set. O boss Vampire King (350.000 de vida) nasce na mesma região: capture o nome dele como alerta e o bot para quando ele aparecer.", 13f, Pal.SUB), 6)
        hero.put(button(if (active) "Reaplicar perfil Vampire" else "Aplicar perfil Vampire", Pal.OK) {
            confirm("Trocar os ajustes de busca, tempos e passeio pelos do perfil Vampire? Seus alvos capturados e botões continuam salvos.") {
                Vampire.applyProfile(cfg)
                Toast.makeText(this, "Perfil Vampire aplicado!", Toast.LENGTH_SHORT).show()
                render(false)
            }
        }, 12)
        add(hero, 4)
        statsCard()
        if (!active) {
            tip("Comece por aqui", "Toque em \"Aplicar perfil Vampire\". Ele ajusta o bot para o Vampire Lair e cria os espaços de captura (vampires, itens, alertas, poções).")
            return
        }

        val miss = Vampire.missing(cfg)
        val chk = card()
        chk.put(text(if (miss.isEmpty()) "Pronto para caçar" else "Falta para começar", 16f, if (miss.isEmpty()) Pal.OK else Pal.WARN, true), 0)
        if (miss.isEmpty()) {
            chk.put(text("O essencial está configurado. Os itens e alertas abaixo são opcionais, mas quanto mais você capturar, melhor.", 12f, Pal.SUB), 4)
        } else {
            for (m in miss) chk.put(text("• $m", 13f, Pal.TXT), 4)
        }
        add(chk, 12)
        if (svc != null && miss.isEmpty()) {
            if (svc.engine.running) {
                add(button("Parar bot", Pal.BAD) { svc.engine.stop(); render(false) }, 10)
            } else {
                add(button("Iniciar hunt", Pal.OK) {
                    if (Build.VERSION.SDK_INT < 30) {
                        info("Android muito antigo", "Precisa de Android 11 ou mais novo.")
                    } else {
                        svc.showPanel()
                        svc.engine.start()
                        Toast.makeText(this, "Bot iniciado! Abra o jogo no Vampire Lair. Pare pelo painel flutuante.", Toast.LENGTH_LONG).show()
                        moveTaskToBack(true)
                    }
                }, 10)
            }
        } else if (svc == null) {
            tip("Serviço desligado", "Ative o serviço de acessibilidade na aba Início para poder capturar e iniciar.")
        }

        slotCard("1. Monstros", "Fique perto de um vampire com o nome aparecendo e capture o nome dele. Capture o Red Vampire também, se for caçar os dois.", Vampire.monsters)
        slotCard("2. Loot", "Mate um vampire e capture cada item caído no chão, um por vez (recorte justo no item). O bot só pega o que você capturou, então pule o que não quer.", Vampire.loot)

        val pots = card()
        pots.put(text("3. Poções e skills", 16f, Pal.TXT, true), 0)
        pots.put(text("Poção de vida: aponte o botão dela e arraste uma linha na barra de vida CHEIA. O perfil bebe abaixo de 55% e, abaixo de 30%, bebe quase sem esperar a recarga. Para pela segurança se a vida ficar abaixo de 15% por 4 s.", 12f, Pal.SUB), 4)
        for (a in cfg.actions) {
            val ready = a.btn != null && (a.type == ActionType.SKILL || a.bar2 != null)
            val tail = if (!a.enabled) "desligada" else if (a.btn == null) "falta o botão" else if (a.type != ActionType.SKILL && a.bar2 == null) "falta a barra" else "ok"
            pots.status(a.name, ready && a.enabled, tail)
        }
        pots.put(button("Configurar na aba Ações", Pal.ACC) { tab = 3; render(true) }, 12)
        add(pots, 12)

        routeCard()

        slotCard("4. Segurança (recomendado)", "Capture estes textos quando aparecerem. Se algum surgir na tela, o bot para na hora e o celular vibra.", Vampire.alerts)
    }

    // ---------- ALVOS ----------
    private fun pageTargets() {
        tip("Como o app acha as coisas",
            "O app não lê letras: ele compara uma FOTO. Você recorta o nome do monstro (ou o item) da tela e ele procura essa imagem no jogo.\n\n" +
                "• Recorte justo, só o texto do nome, sem muito cenário em volta.\n" +
                "• Se o monstro tem nomes diferentes, capture um de cada.\n" +
                "• Depois de trocar a resolução ou o tamanho da interface do jogo, capture de novo.")
        targetSection(0)
        targetSection(1)
        targetSection(2)
    }

    private fun kindOf(t: Target): Int = if (t.alert) 2 else if (t.isItem) 1 else 0

    private fun targetSection(kind: Int) {
        val list = Store.cfg.targets.filter { kindOf(it) == kind }
        val title = when (kind) {
            1 -> "Itens do chão (por foto)"
            2 -> "Alertas (param o bot)"
            else -> "Monstros (pelo nome)"
        }
        add(text(title, 18f, Pal.TXT, true), 18)
        if (kind == 2) {
            add(text("Se um destes textos aparecer na tela (boss, tela de morte...), o bot para na hora e o celular vibra.", 12f, Pal.SUB), 4)
        }
        if (list.isEmpty()) add(text("Nenhum ainda.", 13f, Pal.SUB), 6)
        for (t in list) add(targetRow(t), 8)
        val label = when (kind) {
            1 -> "Capturar item"
            2 -> "Capturar alerta"
            else -> "Capturar nome de monstro"
        }
        add(button(label) { captureTarget(kind) }, 10)
    }

    private fun targetRow(t: Target): View {
        val c = card()
        val r = LinearLayout(this)
        r.orientation = HORZ
        r.gravity = Gravity.CENTER_VERTICAL
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        img.setBackgroundColor(Pal.BG)
        val bm: Bitmap? = BitmapFactory.decodeFile(File(Store.tplDir(), t.file).path)
        if (bm != null) img.setImageBitmap(bm)
        img.setOnClickListener { captureTarget(kindOf(t), t) }
        r.addView(img, LinearLayout.LayoutParams(dp(90), dp(44)))
        val nm = text(t.name, 15f, Pal.TXT, true)
        nm.setPadding(dp(12), 0, dp(8), 0)
        nm.setOnClickListener { rename(t) }
        r.addView(nm, LinearLayout.LayoutParams(0, WC, 1f))
        val sw = SwitchCompat(this)
        sw.isChecked = t.enabled
        sw.setOnCheckedChangeListener { _, v -> t.enabled = v; Store.save() }
        r.addView(sw)
        val del = IconView(this, IconView.TRASH, Pal.BAD)
        del.setOnClickListener {
            confirm("Apagar \"${t.name}\"?") {
                File(Store.tplDir(), t.file).delete()
                Store.cfg.targets.remove(t)
                Store.save()
                render(false)
            }
        }
        val lp = LinearLayout.LayoutParams(dp(34), dp(34))
        lp.leftMargin = dp(8)
        r.addView(del, lp)
        c.put(r, 0)
        c.put(text(if (t.ready()) "Toque no nome para renomear, na foto para refazer." else "Sem foto ainda: toque no quadro escuro para capturar.", 11f, if (t.ready()) Pal.SUB else Pal.WARN), 4)
        return c
    }

    private fun rename(t: Target) {
        val e = EditText(this)
        e.setText(t.name)
        val lay = LinearLayout(this)
        lay.setPadding(dp(20), dp(10), dp(20), 0)
        lay.addView(e, LinearLayout.LayoutParams(MP, WC))
        AlertDialog.Builder(this).setTitle("Renomear").setView(lay)
            .setPositiveButton("Salvar") { _, _ ->
                t.name = e.text.toString().ifBlank { t.name }
                Store.save()
                render(false)
            }
            .setNegativeButton("Cancelar", null).show()
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

    // ---------- AÇÕES ----------
    private fun pageActions() {
        tip("Poções e skills",
            "• Botão: onde fica o ícone da poção/skill na tela do jogo. O app vai tocar ali.\n\n" +
                "• Poção (vida/mana) com barra: arraste uma linha do começo ao fim da barra, com a barra CHEIA. O app mede quanto ainda está cheio e aperta a poção quando ficar abaixo da porcentagem que você escolher em Editar (padrão 50%).\n\n" +
                "• Poção sem barra: aperta de tempos em tempos (recarga).\n\n" +
                "• Skill: o app aperta a cada \"recarga\" segundos enquanto estiver vendo um monstro.")
        for (a in Store.cfg.actions) add(actionCard(a), 12)
        add(button("Adicionar poção ou skill") { addAction() }, 14)
    }

    private fun addAction() {
        val types = ActionType.values()
        AlertDialog.Builder(this).setTitle("O que adicionar?")
            .setItems(types.map { it.label }.toTypedArray()) { _, i ->
                val ty = types[i]
                val n = Store.cfg.actions.count { it.type == ty } + 1
                val a = BotAction(System.currentTimeMillis().toString(), if (n > 1) "${ty.label} $n" else ty.label, ty)
                a.cooldownSec = if (ty == ActionType.SKILL) 3f else 5f
                Store.cfg.actions.add(a)
                Store.save()
                render(false)
            }.show()
    }

    private fun actionCard(a: BotAction): View {
        val c = card()
        val top = LinearLayout(this)
        top.orientation = HORZ
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(text(a.name, 16f, Pal.TXT, true), LinearLayout.LayoutParams(0, WC, 1f))
        val sw = SwitchCompat(this)
        sw.isChecked = a.enabled
        sw.setOnCheckedChangeListener { _, v -> a.enabled = v; Store.save() }
        top.addView(sw)
        val del = IconView(this, IconView.TRASH, Pal.BAD)
        del.setOnClickListener {
            confirm("Apagar \"${a.name}\"?") { Store.cfg.actions.remove(a); Store.save(); render(false) }
        }
        val dl = LinearLayout.LayoutParams(dp(34), dp(34))
        dl.leftMargin = dp(8)
        top.addView(del, dl)
        c.put(top, 0)
        c.put(text(a.type.label, 12f, Pal.ACC), 2)
        c.put(text("Botão: " + if (a.btn != null) "definido" else "NÃO definido", 13f, if (a.btn != null) Pal.OK else Pal.WARN), 8)
        if (a.type != ActionType.SKILL) {
            c.put(text("Barra: " + (if (a.bar2 != null) "definida (bebe abaixo de ${a.pct}%" + (if (a.urgentPct > 0) ", urgente abaixo de ${a.urgentPct}%)" else ")") else if (a.bar != null) "definida (modo ponto)" else "não definida (usa só a recarga)"), 13f, Pal.SUB), 2)
        }
        c.put(text("Recarga: ${a.cooldownSec} s", 13f, Pal.SUB), 2)
        val row = LinearLayout(this)
        row.orientation = HORZ
        val l1 = LinearLayout.LayoutParams(0, WC, 1f)
        l1.rightMargin = dp(4)
        row.addView(button("Botão", Pal.ACC) { setActionBtn(a) }, l1)
        if (a.type != ActionType.SKILL) {
            val l2 = LinearLayout.LayoutParams(0, WC, 1f)
            l2.leftMargin = dp(4); l2.rightMargin = dp(4)
            row.addView(button("Barra", Pal.ACC) { setActionBar(a) }, l2)
        }
        val l3 = LinearLayout.LayoutParams(0, WC, 1f)
        l3.leftMargin = dp(4)
        row.addView(button("Editar", Pal.CARD2) { editAction(a) }, l3)
        c.put(row, 12)
        return c
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

    // ---------- MAPA ----------
    private fun pageMap() {
        val cfg = Store.cfg
        tip("Trocar de lugar",
            "Quando o app não vê nenhum monstro por alguns segundos, ele passeia (toca em pontos do chão ao redor do personagem) ou, se você ligar a opção do mapa, toca no botão do mapa, toca num dos destinos que você marcou, fecha o mapa (se você definiu o botão de fechar) e caminha. Se achar um monstro no caminho, para e ataca.\n\n" +
                "Para marcar um destino: abra o MAPA no jogo ANTES de tocar em \"Capturar agora\", e toque no lugar desejado.")
        routeCard()
        toggle("Usar o mapa para trocar de lugar", "Desligado (padrão): o app passeia tocando no chão perto do personagem. No Rucoy Online se anda tocando na tela. Ligado: usa o botão do mapa e os destinos abaixo.", cfg.useMap) { cfg.useMap = it }
        toggle("Passeio inteligente (explorar)", "Ligado: anda em linha reta numa direção, percebe quando bate na parede (a tela não anda) e troca de direção. Desligado: toques aleatórios ao redor do personagem.", cfg.explore) { cfg.explore = it }
        slider("Alcance do passeio", "Até que distância do personagem o app toca para andar (porcentagem da largura da tela).",
            10, 45, cfg.wanderPct, { "$it%" }) { cfg.wanderPct = it }
        slider("Tempo andando por toque", "Quanto o app espera andando depois de cada toque de passeio.",
            10, 80, (cfg.wanderSec * 10).toInt(), { "${it / 10.0} s" }) { cfg.wanderSec = it / 10f }
        val c1 = card()
        c1.put(text("Botão que abre o mapa", 15f, Pal.TXT, true), 0)
        c1.put(text(if (cfg.mapBtn != null) "definido" else "NÃO definido", 13f, if (cfg.mapBtn != null) Pal.OK else Pal.WARN), 2)
        c1.put(button("Definir botão do mapa") {
            val s = needService()
            if (s != null) {
                s.startCapture("Abra o jogo na tela normal (mapa fechado). Depois toque em \"Capturar agora\".",
                    "Toque no botão que ABRE o mapa e depois em Confirmar.", false) { r -> cfg.mapBtn = fromCap(r); Store.save() }
                moveTaskToBack(true)
            }
        }, 10)
        add(c1, 12)
        val c2 = card()
        c2.put(text("Botão de fechar o mapa (opcional)", 15f, Pal.TXT, true), 0)
        c2.put(text("Só precisa se o mapa continua aberto depois de escolher o destino.", 12f, Pal.SUB), 2)
        c2.put(text(if (cfg.mapClose != null) "definido" else "não definido", 13f, if (cfg.mapClose != null) Pal.OK else Pal.SUB), 4)
        val r2 = LinearLayout(this)
        r2.orientation = HORZ
        val q1 = LinearLayout.LayoutParams(0, WC, 1f)
        q1.rightMargin = dp(4)
        r2.addView(button("Definir") {
            val s = needService()
            if (s != null) {
                s.startCapture("Abra o MAPA no jogo. Depois toque em \"Capturar agora\".",
                    "Toque no botão de FECHAR o mapa e depois em Confirmar.", false) { r -> cfg.mapClose = fromCap(r); Store.save() }
                moveTaskToBack(true)
            }
        }, q1)
        if (cfg.mapClose != null) {
            val q2 = LinearLayout.LayoutParams(0, WC, 1f)
            q2.leftMargin = dp(4)
            r2.addView(button("Remover", Pal.CARD2) { cfg.mapClose = null; Store.save(); render(false) }, q2)
        }
        c2.put(r2, 10)
        add(c2)

        add(text("Destinos", 18f, Pal.TXT, true), 18)
        if (cfg.dests.isEmpty()) add(text("Nenhum destino ainda.", 13f, Pal.SUB), 6)
        for (i in cfg.dests.indices) {
            val c = card()
            val r = LinearLayout(this)
            r.orientation = HORZ
            r.gravity = Gravity.CENTER_VERTICAL
            r.addView(text("Destino ${i + 1}", 15f, Pal.TXT, true), LinearLayout.LayoutParams(0, WC, 1f))
            val del = IconView(this, IconView.TRASH, Pal.BAD)
            del.setOnClickListener { cfg.dests.removeAt(i); Store.save(); render(false) }
            r.addView(del, LinearLayout.LayoutParams(dp(34), dp(34)))
            c.put(r, 0)
            add(c, 8)
        }
        add(button("Adicionar destino") {
            val s = needService()
            if (s != null) {
                s.startCapture("Abra o MAPA no jogo. Depois toque em \"Capturar agora\".",
                    "Toque no lugar do mapa para onde ir e depois em Confirmar.", false) { r -> cfg.dests.add(fromCap(r)); Store.save() }
                moveTaskToBack(true)
            }
        }, 10)

        slider("Tempo de caminhada", "Quanto tempo o app espera andando até o destino (se achar monstro, ele para antes).",
            2, 60, cfg.travelSec.toInt(), { "$it s" }) { cfg.travelSec = it.toFloat() }
        slider("Espera ao abrir o mapa", "Tempo para o mapa abrir antes de tocar no destino.",
            3, 40, (cfg.mapOpenSec * 10).toInt(), { "${it / 10.0} s" }) { cfg.mapOpenSec = it / 10f }
        slider("Sem monstro por quanto tempo?", "Quantos segundos sem ver monstro até abrir o mapa.",
            1, 30, cfg.noMonsterSec.toInt(), { "$it s" }) { cfg.noMonsterSec = it.toFloat() }
        toggle("Destinos em ordem aleatória", "Desligado: vai passando pelos destinos na ordem da lista.", cfg.randomDest) { cfg.randomDest = it }
    }

    private fun applyRucoy() {
        val c = Store.cfg
        c.scanMs = 700
        c.reclickSec = 3f
        c.offsetPx = 45
        c.monScore = 70
        c.itemScore = 72
        c.lootFirst = true
        c.nearest = true
        c.giveUpSec = 40
        c.useMap = false
        c.wanderPct = 30
        c.wanderSec = 2.5f
        c.noMonsterSec = 3f
        c.mTop = 0
        c.mBottom = 0
        c.mLeft = 0
        c.mRight = 0
        c.profile = ""
        c.explore = false
        c.chaseSec = 3f
        c.lootSweepSec = 0f
        c.lootNearPct = 0
        c.lootTries = 3
        c.panicPct = 0
        Store.save()
    }

    // ---------- AJUSTES ----------
    private fun pageSettings() {
        val cfg = Store.cfg
        tip("Ajustes finos",
            "Cada item abaixo explica o que faz. Mexa com calma: o padrão já funciona na maioria dos jogos.")
        val pr = card()
        pr.put(text("Rucoy Online", 16f, Pal.TXT, true), 0)
        pr.put(text("Aplica valores iniciais que costumam servir no Rucoy (nomes pequenos, personagem no centro, andar tocando no chão). Depois ajuste testando.", 12f, Pal.SUB), 4)
        pr.put(button("Aplicar valores do Rucoy", Pal.OK) {
            confirm("Trocar os ajustes de busca e tempos pelos valores do Rucoy?") { applyRucoy(); render(false) }
        }, 10)
        add(pr)
        slider("Velocidade da busca", "De quanto em quanto tempo o app olha a tela. Menor = reage mais rápido, mas gasta mais bateria.",
            4, 20, cfg.scanMs / 100, { "${it / 10.0} s" }) { cfg.scanMs = it * 100 }
        slider("Tocar de novo no monstro", "Intervalo entre um toque e outro no monstro enquanto ele estiver na tela.",
            5, 100, (cfg.reclickSec * 10).toInt(), { "${it / 10.0} s" }) { cfg.reclickSec = it / 10f }
        slider("Toque abaixo do nome", "O nome fica acima do monstro. Este valor desce o toque para acertar o corpo (em pixels).",
            0, 200, cfg.offsetPx, { "$it px" }) { cfg.offsetPx = it }
        slider("Sensibilidade para monstros", "Quanto a imagem precisa ser parecida. Se não acha o monstro, diminua. Se toca onde não deve, aumente.",
            40, 95, cfg.monScore, { "$it%" }) { cfg.monScore = it }
        slider("Sensibilidade para itens", "O mesmo, para os itens do chão.",
            40, 95, cfg.itemScore, { "$it%" }) { cfg.itemScore = it }
        toggle("Atacar o monstro mais perto", "Ligado: entre vários monstros, vai no mais perto do centro da tela (onde fica o personagem). Desligado: vai no que mais se parece com a foto.", cfg.nearest) { cfg.nearest = it }
        slider("Desistir do monstro após", "Se ficar tentando o mesmo lugar por tanto tempo (monstro inalcançável), ignora por um tempo.",
            10, 120, cfg.giveUpSec, { "$it s" }) { cfg.giveUpSec = it }
        slider("Perseguir monstro que foge", "Intervalo entre toques enquanto o monstro está se mexendo (vampire foge com a vida vermelha). Menor = persegue mais rápido.",
            5, 30, (cfg.chaseSec * 10).toInt(), { "${it / 10.0} s" }) { cfg.chaseSec = it / 10f }
        slider("Varrer loot depois do abate", "Depois que o monstro some, espera e procura itens no chão por este tempo antes de sair andando.",
            0, 15, cfg.lootSweepSec.toInt(), { if (it == 0) "desligado" else "$it s" }) { cfg.lootSweepSec = it.toFloat() }
        slider("Pegar item perto durante a luta", "Mesmo com monstro por perto, pega itens que caíram até esta distância do personagem (se a vida estiver acima de 50%). 0 = só pega quando não há monstro.",
            0, 50, cfg.lootNearPct, { if (it == 0) "desligado" else "$it%" }) { cfg.lootNearPct = it }
        slider("Tentativas por item", "Quantas vezes toca no mesmo item antes de desistir dele (parede no caminho, por exemplo).",
            2, 10, cfg.lootTries, { "$it" }) { cfg.lootTries = it }
        slider("Parar se a vida ficar crítica", "Se a vida ficar abaixo disso mesmo bebendo poção, o bot para e o celular vibra. Precisa da barra de vida configurada.",
            0, 40, cfg.panicPct, { if (it == 0) "desligado" else "$it%" }) { cfg.panicPct = it }
        slider("Tempo em vida crítica", "Quanto tempo a vida precisa ficar crítica antes de parar.",
            2, 15, cfg.panicSec, { "$it s" }) { cfg.panicSec = it }
        toggle("Pegar itens antes de atacar", "Ligado: se houver item no chão, pega primeiro. Desligado: só pega quando não houver monstro.", cfg.lootFirst) { cfg.lootFirst = it }
        add(text("Área de busca", 18f, Pal.TXT, true), 18)
        add(text("Ignore partes da tela onde aparece o nome do seu personagem, chat ou barras, para o app não confundir.", 12f, Pal.SUB), 4)
        slider("Ignorar topo", "Porcentagem da tela, de cima para baixo.", 0, 40, cfg.mTop, { "$it%" }) { cfg.mTop = it }
        slider("Ignorar base", "Porcentagem da tela, de baixo para cima.", 0, 40, cfg.mBottom, { "$it%" }) { cfg.mBottom = it }
        slider("Ignorar esquerda", "Porcentagem da tela, da esquerda para a direita.", 0, 40, cfg.mLeft, { "$it%" }) { cfg.mLeft = it }
        slider("Ignorar direita", "Porcentagem da tela, da direita para a esquerda.", 0, 40, cfg.mRight, { "$it%" }) { cfg.mRight = it }

        add(text("Aplicativo", 18f, Pal.TXT, true), 18)
        val c = card()
        c.put(text("Versão ${Updater.installedName(this)}", 14f, Pal.TXT, true), 0)
        c.put(button("Verificar atualização") { Updater.manualCheck(this) }, 10)
        c.put(button("Ver o que mudou", Pal.CARD2) { Novidades.showLatest(this) }, 8)
        add(c)

        val h = card()
        h.put(text("Ajuda", 16f, Pal.TXT, true), 0)
        h.put(text(
            "• Tela preta ao capturar: o jogo bloqueia capturas. Não há como contornar.\n" +
                "• Não acha o monstro: capture o nome de novo (recorte justo) ou diminua a sensibilidade.\n" +
                "• Toca no lugar errado: confira o \"Toque abaixo do nome\" e a área de busca.\n" +
                "• Girou a tela do celular? Os pontos continuam valendo se o jogo mantiver a mesma orientação.\n" +
                "• Muitas imagens deixam a busca mais lenta: use só as necessárias.\n" +
                "• O serviço desliga sozinho? Alguns celulares matam apps em segundo plano: tire o Auto Caçador da economia de bateria.", 12f, Pal.SUB), 8)
        add(h)
    }
}
