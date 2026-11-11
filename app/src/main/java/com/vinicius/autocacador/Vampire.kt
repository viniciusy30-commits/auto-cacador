package com.vinicius.autocacador

/**
 * Perfil da Hunt de Vampire (Vampire Lair / Vampire Dungeon do Rucoy Online).
 *
 * Dados conferidos na wiki do Rucoy (rucoy-online.fandom.com):
 *  - Vampire (Lv 100): enche o 1º andar e também aparece no 2º. Com vida baixa ele FOGE de quem chega perto.
 *    Gold 60–139. Drops: Greater Mana Potion, Greater Health Potion, Life Pendant (raro),
 *    Soldier Glove Left, Soldier Belt, Soldier Boots.
 *  - Red Vampire (Lv 110): só no 2º andar. Gold 76–150. Drops: Greater Mana/Health Potion, Life Pendant (raro),
 *    Soldier Helmet, Soldier Armor, Soldier Legs, Silver Backpack. (A wiki avisa que a tabela está incompleta.)
 *  - Vampire King: boss da região (350.000 de vida), avisa no chat. O bot só para quando ele aparece.
 */
object Vampire {
    const val KIND_MON = 0
    const val KIND_ITEM = 1
    const val KIND_ALERT = 2

    class Slot(val key: String, val name: String, val kind: Int, val hint: String)

    val monsters = listOf(
        Slot("vamp", "Vampire (Lv 100)", KIND_MON, "Recorte só o NOME que aparece em cima do Vampire."),
        Slot("redvamp", "Red Vampire (Lv 110)", KIND_MON, "Só existe no 2º andar. Recorte só o NOME que aparece em cima dele.")
    )

    val loot = listOf(
        Slot("item_gold", "Gold (moedas)", KIND_ITEM, "Moedas caídas no chão. É o loot mais comum: capture este primeiro."),
        Slot("item_ghp", "Greater Health Potion", KIND_ITEM, "A poção caída no chão."),
        Slot("item_gmp", "Greater Mana Potion", KIND_ITEM, "A poção caída no chão."),
        Slot("item_pendant", "Life Pendant", KIND_ITEM, "Raro. Capture assim que cair um."),
        Slot("item_glove", "Soldier Glove Left", KIND_ITEM, "Peça do Soldier Set (Vampire)."),
        Slot("item_belt", "Soldier Belt", KIND_ITEM, "Peça do Soldier Set (Vampire)."),
        Slot("item_boots", "Soldier Boots", KIND_ITEM, "Peça do Soldier Set (Vampire)."),
        Slot("item_helmet", "Soldier Helmet", KIND_ITEM, "Peça do Soldier Set (Red Vampire)."),
        Slot("item_armor", "Soldier Armor", KIND_ITEM, "Peça do Soldier Set (Red Vampire)."),
        Slot("item_legs", "Soldier Legs", KIND_ITEM, "Peça do Soldier Set (Red Vampire)."),
        Slot("item_backpack", "Silver Backpack", KIND_ITEM, "Cai do Red Vampire.")
    )

    val alerts = listOf(
        Slot("king", "Vampire King (boss)", KIND_ALERT, "Capture o nome do boss quando ele aparecer. O bot para na hora: ele tem 350.000 de vida."),
        Slot("dead", "Tela de morte / desconexão", KIND_ALERT, "Opcional: recorte o texto ou botão que aparece quando você morre ou cai da conta.")
    )

    val all: List<Slot> get() = monsters + loot + alerts

    /** Cria os espaços (vazios) para capturar monstros, itens e alertas, sem apagar nada. */
    fun ensure(c: Cfg) {
        for (s in all) {
            if (c.targets.none { it.slot == s.key }) {
                val t = Target("slot_" + s.key, s.name, s.kind == KIND_ITEM, "")
                t.alert = s.kind == KIND_ALERT
                t.slot = s.key
                c.targets.add(t)
            }
        }
    }

    /** Cria poção de vida/mana e skill se você ainda não tem. Não mexe no que você já configurou. */
    fun ensureActions(c: Cfg) {
        val hps = c.actions.filter { it.type == ActionType.HP }
        if (hps.isEmpty()) {
            val a = BotAction("vamp_hp", "Greater Health Potion", ActionType.HP)
            a.cooldownSec = 3f
            a.pct = 55
            a.urgentPct = 30
            c.actions.add(a)
        } else {
            for (a in hps) if (a.urgentPct == 0) a.urgentPct = 30
        }
        if (c.actions.none { it.type == ActionType.MP }) {
            val a = BotAction("vamp_mp", "Greater Mana Potion", ActionType.MP)
            a.cooldownSec = 4f
            a.pct = 35
            a.enabled = false // só precisa para quem gasta mana (mago)
            c.actions.add(a)
        }
        if (c.actions.none { it.type == ActionType.SKILL }) {
            val a = BotAction("vamp_skill", "Skill 1", ActionType.SKILL)
            a.cooldownSec = 3f
            a.enabled = false // liga quando você definir o botão da skill
            c.actions.add(a)
        }
    }

    /** Aplica os valores de busca/tempo pensados para o Vampire Lair. */
    fun applyProfile(c: Cfg) {
        c.profile = "vampire"
        c.scanMs = 500          // olha a tela mais rápido: vampire bate forte
        c.reclickSec = 2.0f
        c.chaseSec = 1.0f       // vampire foge com vida baixa: reclica rápido enquanto ele se mexe
        c.offsetPx = 45
        c.monScore = 70
        c.itemScore = 72
        c.nearest = true
        c.giveUpSec = 60
        c.lootFirst = false     // luta primeiro...
        c.lootNearPct = 25      // ...mas pega o que cair bem perto, se a vida estiver boa
        c.lootSweepSec = 4f     // e varre o chão por 4 s depois de cada abate
        c.lootTries = 5
        c.useMap = false
        c.explore = true        // passeio em linha reta, troca de direção quando bate na parede
        c.wanderPct = 32
        c.wanderSec = 2.5f
        c.noMonsterSec = 4f
        c.panicPct = 15         // vida crítica por 4 s mesmo bebendo: para e vibra
        c.panicSec = 4
        c.idleStopMin = 5       // 5 min sem ver vampire nem pegar item: para e vibra
        c.tapJitter = 6
        ensure(c)
        ensureActions(c)
        Store.save()
    }

    /** Passos obrigatórios que ainda faltam (lista vazia = pronto para caçar). */
    fun missing(c: Cfg): List<String> {
        val out = ArrayList<String>()
        if (c.targets.none { !it.isItem && !it.alert && it.enabled && it.ready() }) out.add("capturar o nome de pelo menos 1 vampire")
        val hp = c.actions.firstOrNull { it.type == ActionType.HP && it.enabled }
        if (hp == null || hp.btn == null) out.add("definir o botão da poção de vida")
        else if (hp.bar2 == null) out.add("definir a barra de vida (arraste uma linha na barra cheia)")
        return out
    }

    /** O que ainda ajudaria muito, mas não é obrigatório. */
    fun suggestions(c: Cfg): List<String> {
        val out = ArrayList<String>()
        if (c.targets.none { it.slot == "item_gold" && it.ready() }) out.add("capturar o Gold (loot mais comum)")
        if (c.targets.none { it.alert && it.enabled && it.ready() && it.slot == "king" }) out.add("capturar o nome do Vampire King (para o bot quando o boss aparece)")
        if (c.route.size < 2) out.add("gravar a rota da hunt (ou deixar o passeio automático)")
        return out
    }
}
