package com.vinicius.autocacador

import android.app.Activity
import androidx.appcompat.app.AlertDialog

object Novidades {
    class Sec(val id: String, val title: String, val items: ArrayList<String>)

    fun parse(a: Activity): List<Sec> {
        val out = ArrayList<Sec>()
        try {
            val txt = a.assets.open("novidades.txt").bufferedReader().use { it.readText() }
            var cur: Sec? = null
            for (raw in txt.lines()) {
                val l = raw.trim()
                if (l.startsWith("#id ")) {
                    cur = Sec(l.substring(4).trim(), "", ArrayList())
                    out.add(cur)
                } else if (l.startsWith("#titulo ") && cur != null) {
                    val s = Sec(cur.id, l.substring(8).trim(), cur.items)
                    out[out.size - 1] = s
                    cur = s
                } else if (l.startsWith("- ") && cur != null) {
                    cur.items.add(l.substring(2).trim())
                }
            }
        } catch (e: Exception) { }
        return out
    }

    fun show(a: Activity, s: Sec) {
        AlertDialog.Builder(a)
            .setTitle("O que mudou: " + s.title)
            .setMessage(s.items.joinToString("\n") { "• $it" })
            .setPositiveButton("Legal", null)
            .show()
    }

    fun showLatest(a: Activity) {
        val l = parse(a)
        if (l.isNotEmpty()) show(a, l[0])
    }

    /** Mostra só na primeira abertura depois de uma atualização. */
    fun maybeShow(a: Activity) {
        val l = parse(a)
        if (l.isEmpty()) return
        val p = a.getSharedPreferences("nov", 0)
        val last = p.getString("last", null)
        if (last != l[0].id) {
            p.edit().putString("last", l[0].id).apply()
            if (last != null) show(a, l[0])
        }
    }
}
