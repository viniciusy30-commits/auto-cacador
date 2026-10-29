package com.vinicius.autocacador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Ponto na tela como fração (0..1) da largura/altura, funciona em qualquer orientação. */
data class Pt(val fx: Float, val fy: Float)

enum class ActionType(val label: String) {
    HP("Poção de vida"),
    MP("Poção de mana"),
    SKILL("Skill")
}

class BotAction(var id: String, var name: String, var type: ActionType) {
    var enabled = true
    var cooldownSec = 3f
    var btn: Pt? = null
    var bar: Pt? = null
    var barColor = 0
    var bar2: Pt? = null
    var pct = 50
}

class Target(var id: String, var name: String, var isItem: Boolean, var file: String) {
    var enabled = true
}

class Cfg {
    val actions = ArrayList<BotAction>()
    val targets = ArrayList<Target>()
    val dests = ArrayList<Pt>()
    var mapBtn: Pt? = null
    var mapClose: Pt? = null
    var mapOpenSec = 1.2f
    var travelSec = 8f
    var randomDest = false
    var noMonsterSec = 4f
    var scanMs = 700
    var reclickSec = 2.5f
    var offsetPx = 50
    var monScore = 70
    var itemScore = 75
    var lootFirst = true
    var mTop = 0
    var mBottom = 0
    var mLeft = 0
    var mRight = 0
    var useMap = false
    var wanderPct = 30
    var wanderSec = 2.5f
    var nearest = true
    var giveUpSec = 40

    private fun ptJs(p: Pt?): Any =
        if (p == null) JSONObject.NULL else JSONObject().put("x", p.fx.toDouble()).put("y", p.fy.toDouble())

    fun toJson(): JSONObject {
        val o = JSONObject()
        val aa = JSONArray()
        for (a in actions) {
            aa.put(
                JSONObject().put("id", a.id).put("name", a.name).put("type", a.type.name)
                    .put("en", a.enabled).put("cd", a.cooldownSec.toDouble())
                    .put("btn", ptJs(a.btn)).put("bar", ptJs(a.bar)).put("bar2", ptJs(a.bar2)).put("pct", a.pct).put("col", a.barColor)
            )
        }
        o.put("actions", aa)
        val tt = JSONArray()
        for (t in targets) {
            tt.put(
                JSONObject().put("id", t.id).put("name", t.name).put("item", t.isItem)
                    .put("file", t.file).put("en", t.enabled)
            )
        }
        o.put("targets", tt)
        val dd = JSONArray()
        for (d in dests) dd.put(ptJs(d))
        o.put("dests", dd)
        o.put("mapBtn", ptJs(mapBtn))
        o.put("mapClose", ptJs(mapClose))
        o.put("mapOpenSec", mapOpenSec.toDouble())
        o.put("travelSec", travelSec.toDouble())
        o.put("randomDest", randomDest)
        o.put("noMonsterSec", noMonsterSec.toDouble())
        o.put("scanMs", scanMs)
        o.put("reclickSec", reclickSec.toDouble())
        o.put("offsetPx", offsetPx)
        o.put("monScore", monScore)
        o.put("itemScore", itemScore)
        o.put("lootFirst", lootFirst)
        o.put("mTop", mTop)
        o.put("mBottom", mBottom)
        o.put("mLeft", mLeft)
        o.put("mRight", mRight)
        o.put("useMap", useMap)
        o.put("wanderPct", wanderPct)
        o.put("wanderSec", wanderSec.toDouble())
        o.put("nearest", nearest)
        o.put("giveUpSec", giveUpSec)
        return o
    }

    companion object {
        private fun rd(o: JSONObject?): Pt? =
            if (o == null) null else Pt(o.optDouble("x", 0.0).toFloat(), o.optDouble("y", 0.0).toFloat())

        fun fromJson(o: JSONObject): Cfg {
            val c = Cfg()
            val aa = o.optJSONArray("actions") ?: JSONArray()
            for (i in 0 until aa.length()) {
                val j = aa.getJSONObject(i)
                val type = try { ActionType.valueOf(j.optString("type", "SKILL")) } catch (e: Exception) { ActionType.SKILL }
                val a = BotAction(j.optString("id"), j.optString("name"), type)
                a.enabled = j.optBoolean("en", true)
                a.cooldownSec = j.optDouble("cd", 3.0).toFloat()
                a.btn = rd(j.optJSONObject("btn"))
                a.bar = rd(j.optJSONObject("bar"))
                a.bar2 = rd(j.optJSONObject("bar2"))
                a.pct = j.optInt("pct", 50)
                a.barColor = j.optInt("col", 0)
                c.actions.add(a)
            }
            val tt = o.optJSONArray("targets") ?: JSONArray()
            for (i in 0 until tt.length()) {
                val j = tt.getJSONObject(i)
                val t = Target(j.optString("id"), j.optString("name"), j.optBoolean("item"), j.optString("file"))
                t.enabled = j.optBoolean("en", true)
                c.targets.add(t)
            }
            val dd = o.optJSONArray("dests") ?: JSONArray()
            for (i in 0 until dd.length()) rd(dd.optJSONObject(i))?.let { c.dests.add(it) }
            c.mapBtn = rd(o.optJSONObject("mapBtn"))
            c.mapClose = rd(o.optJSONObject("mapClose"))
            c.mapOpenSec = o.optDouble("mapOpenSec", 1.2).toFloat()
            c.travelSec = o.optDouble("travelSec", 8.0).toFloat()
            c.randomDest = o.optBoolean("randomDest", false)
            c.noMonsterSec = o.optDouble("noMonsterSec", 4.0).toFloat()
            c.scanMs = o.optInt("scanMs", 700)
            c.reclickSec = o.optDouble("reclickSec", 2.5).toFloat()
            c.offsetPx = o.optInt("offsetPx", 50)
            c.monScore = o.optInt("monScore", 70)
            c.itemScore = o.optInt("itemScore", 75)
            c.lootFirst = o.optBoolean("lootFirst", true)
            c.mTop = o.optInt("mTop", 0)
            c.mBottom = o.optInt("mBottom", 0)
            c.mLeft = o.optInt("mLeft", 0)
            c.mRight = o.optInt("mRight", 0)
            c.useMap = o.optBoolean("useMap", false)
            c.wanderPct = o.optInt("wanderPct", 30)
            c.wanderSec = o.optDouble("wanderSec", 2.5).toFloat()
            c.nearest = o.optBoolean("nearest", true)
            c.giveUpSec = o.optInt("giveUpSec", 40)
            return c
        }
    }
}

object Store {
    private var ctx: Context? = null
    var cfg = Cfg()

    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        val s = ctx!!.getSharedPreferences("cfg", 0).getString("json", null)
        if (s != null) {
            try { cfg = Cfg.fromJson(JSONObject(s)) } catch (e: Exception) { }
        }
    }

    fun save() {
        ctx?.getSharedPreferences("cfg", 0)?.edit()?.putString("json", cfg.toJson().toString())?.apply()
    }

    fun tplDir(): File {
        val d = File(ctx!!.filesDir, "tpl")
        d.mkdirs()
        return d
    }
}
