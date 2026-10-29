package com.vinicius.autocacador

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    const val REPO = "viniciusy30-commits/auto-cacador"

    class Info(val code: Int, val url: String, val tag: String)

    private val main = Handler(Looper.getMainLooper())

    @Suppress("DEPRECATION")
    fun installedCode(a: Activity): Int {
        val pi = a.packageManager.getPackageInfo(a.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }

    fun installedName(a: Activity): String =
        a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "?"

    private fun fetchLatest(): Info? {
        val con = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        con.connectTimeout = 10000
        con.readTimeout = 15000
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.setRequestProperty("User-Agent", "AutoCacador")
        val txt = con.inputStream.bufferedReader().use { it.readText() }
        val j = JSONObject(txt)
        val tag = j.getString("tag_name")
        val code = tag.trim().removePrefix("v").removePrefix("V").toIntOrNull() ?: return null
        val assets = j.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) return Info(code, a.getString("browser_download_url"), tag)
        }
        return null
    }

    /** st: 0 = erro/sem internet, 1 = já está na mais nova, 2 = tem versão nova */
    fun check(a: Activity, cb: (Int, Info?) -> Unit) {
        Thread {
            var st = 0
            var info: Info? = null
            try {
                val i = fetchLatest()
                if (i != null) {
                    info = i
                    st = if (i.code > installedCode(a)) 2 else 1
                }
            } catch (e: Exception) {
                st = 0
            }
            main.post { cb(st, info) }
        }.start()
    }

    fun autoCheck(a: Activity) {
        val p = a.getSharedPreferences("upd", 0)
        val last = p.getLong("last", 0L)
        if (System.currentTimeMillis() - last < 6L * 3600L * 1000L) return
        check(a) { st, info ->
            if (st != 0) p.edit().putLong("last", System.currentTimeMillis()).apply()
            if (st == 2 && info != null && !a.isFinishing) showDialog(a, info)
        }
    }

    fun manualCheck(a: Activity) {
        Toast.makeText(a, "Procurando atualização…", Toast.LENGTH_SHORT).show()
        check(a) { st, info ->
            when {
                a.isFinishing -> {}
                st == 0 -> Toast.makeText(a, "Sem internet ou não consegui consultar o GitHub.", Toast.LENGTH_LONG).show()
                st == 1 -> Toast.makeText(a, "Você já está na versão mais nova.", Toast.LENGTH_LONG).show()
                info != null -> showDialog(a, info)
            }
        }
    }

    private fun showDialog(a: Activity, info: Info) {
        AlertDialog.Builder(a)
            .setTitle("Nova versão disponível")
            .setMessage("Existe a versão ${info.tag} do Auto Caçador. Quer baixar e instalar agora? Seus ajustes e fotos continuam salvos.")
            .setPositiveButton("Atualizar") { _, _ -> start(a, info) }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun canInstall(a: Activity): Boolean =
        Build.VERSION.SDK_INT < 26 || a.packageManager.canRequestPackageInstalls()

    private fun start(a: Activity, info: Info) {
        if (!canInstall(a)) {
            AlertDialog.Builder(a)
                .setTitle("Permitir instalação")
                .setMessage("Para atualizar por dentro do app, o Android precisa liberar \"Instalar apps desconhecidos\" para o Auto Caçador. Toque em Abrir configurações, ative a opção, volte e toque em Atualizar de novo.")
                .setPositiveButton("Abrir configurações") { _, _ ->
                    a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        val lay = LinearLayout(a)
        lay.setPadding(a.dp(24), a.dp(16), a.dp(24), a.dp(8))
        lay.addView(bar, LinearLayout.LayoutParams(MP, WC))
        val dlg = AlertDialog.Builder(a).setTitle("Baixando atualização…").setView(lay).setCancelable(false).show()
        Thread {
            try {
                val dir = File(a.cacheDir, "updates")
                dir.deleteRecursively()
                dir.mkdirs()
                val f = File(dir, "AutoCacador.apk")
                val con = URL(info.url).openConnection() as HttpURLConnection
                con.instanceFollowRedirects = true
                con.connectTimeout = 15000
                con.readTimeout = 30000
                val total = con.contentLength
                var done = 0L
                con.inputStream.use { ins ->
                    f.outputStream().use { outs ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            outs.write(buf, 0, n)
                            done += n
                            if (total > 0) main.post { bar.progress = (done * 100 / total).toInt() }
                        }
                    }
                }
                main.post { dlg.dismiss(); install(a, f) }
            } catch (e: Exception) {
                main.post {
                    dlg.dismiss()
                    Toast.makeText(a, "Falha no download. Tente de novo.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun install(a: Activity, f: File) {
        val uri = FileProvider.getUriForFile(a, a.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, "application/vnd.android.package-archive")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
    }
}
