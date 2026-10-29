package com.vinicius.autocacador

/**
 * Perfil da Hunt de Vampire (Vampire Lair do Rucoy Online).
 *
 * Dados usados (wiki do Rucoy): Vampire Lv 100 (450 HP) e Red Vampire Lv 110 (530 HP).
 * Os dois FOGEM quando a vida deles fica vermelha. Drops: Gold, Greater Health Potion,
 * Greater Mana Potion, Life Pendant, Soldier Glove Left, Soldier Belt e Soldier Boots.
 * O boss Vampire King (350.000 HP) nasce na mesma região e avisa no chat.
 */
object Vampire {
    const val KIND_MON = 0
    const val KIND_ITEM = 1
    const val KIND_ALERT = 2

    class Slot(val key: String, val name: String, val kind: Int, val hint: String)

    val monsters = listOf(
        Slot("vamp", "Vampire (Lv 100)", KIND_MON, "Recorte só o NOME que aparece em cima do Vampire."),
        Slot("redvamp", "Red Vampire (Lv 110)", KIND_MON, "Recorte só o NOME que aparece em cima do Red Vampire.")
    )

    val loot = listOf(
        Slot("item_gold", "Gold (moedas)", KIND_ITEM, "Moedas caídas no chão."),
        Slot("item_ghp", "Greater Health Potion", KIND_ITEM, "A poção caída no chão."),
        Slot("item_gmp", "Greater Mana Potion", KIND_ITEM, "A poção caída no chão."),
        Slot("item_pendant", "Life Pendant", KIND_ITEM, "O primeiro pendant do jogo. Capture assim que cair um."),
        Slot("item_glove", "Soldier Glove Left", KIND_ITEM, "Peça do Soldier Set."),
        Slot("item_belt", "Soldier Belt", KIND_ITEM, "Peça do Soldier Set."),
        Slot("item_boots", "Soldier Boots", KIND_ITEM, "Peça do Soldier Set.")
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
            c.actions.add(a)
        }
    }

    /** Aplica os valores de busca/tempo pensados para o Vampire Lair. */
    fun applyProfile(c: Cfg) {
        c.profile = "vampire"
        c.scanMs = 500          // olha a tela mais rápido: vampire bate forte
        c.reclickSec = 2.0f
        c.chaseSec = 1.0f       // vampire foge com vida vermelha: reclica rápido enquanto ele se mexe
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
}
