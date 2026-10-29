package com.vinicius.autocacador

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CapResult(val full: Bitmap, val x: Int, val y: Int, val crop: Bitmap?, val x2: Int = 0, val y2: Int = 0)

class PickView(ctx: Context, private val bmp: Bitmap, private val region: Boolean, private val line: Boolean = false) : View(ctx) {
    private val drag = region || line
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var x0 = 0f
    private var y0 = 0f
    private var x1 = 0f
    private var y1 = 0f
    private var has = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x0 = e.x; y0 = e.y; x1 = e.x; y1 = e.y; has = true }
            MotionEvent.ACTION_MOVE -> { if (drag) { x1 = e.x; y1 = e.y } else { x0 = e.x; y0 = e.y } }
            MotionEvent.ACTION_UP -> { if (drag) { x1 = e.x; y1 = e.y } else { x0 = e.x; y0 = e.y } }
        }
        invalidate()
        return true
    }

    override fun onDraw(c: Canvas) {
        val dst = Rect(0, 0, width, height)
        c.drawBitmap(bmp, null, dst, null)
        c.drawColor(0x88000000.toInt())
        if (!has) return
        p.strokeWidth = 4f
        if (region) {
            val r = Rect(minOf(x0, x1).toInt(), minOf(y0, y1).toInt(), maxOf(x0, x1).toInt(), maxOf(y0, y1).toInt())
            c.save()
            c.clipRect(r)
            c.drawBitmap(bmp, null, dst, null)
            c.restore()
            p.style = Paint.Style.STROKE
            p.color = Pal.OK
            c.drawRect(r, p)
        } else if (line) {
            p.style = Paint.Style.STROKE
            p.color = Pal.OK
            c.drawLine(x0, y0, x1, y1, p)
            c.drawCircle(x0, y0, 18f, p)
            c.drawCircle(x1, y1, 18f, p)
        } else {
            p.style = Paint.Style.STROKE
            p.color = Pal.OK
            c.drawCircle(x0, y0, 36f, p)
            c.drawLine(x0 - 56f, y0, x0 + 56f, y0, p)
            c.drawLine(x0, y0 - 56f, x0, y0 + 56f, p)
        }
    }

    fun selection(): CapResult? {
        if (!has || width == 0 || height == 0) return null
        val sx = bmp.width / width.toFloat()
        val sy = bmp.height / height.toFloat()
        if (line) {
            if (Math.abs(x1 - x0) < 20f) return null
            return CapResult(bmp, (x0 * sx).toInt().coerceIn(0, bmp.width - 1), (y0 * sy).toInt().coerceIn(0, bmp.height - 1), null, (x1 * sx).toInt().coerceIn(0, bmp.width - 1), (y1 * sy).toInt().coerceIn(0, bmp.height - 1))
        }
        if (!region) {
            return CapResult(bmp, (x0 * sx).toInt().coerceIn(0, bmp.width - 1), (y0 * sy).toInt().coerceIn(0, bmp.height - 1), null)
        }
        val l = (minOf(x0, x1) * sx).toInt().coerceIn(0, bmp.width - 1)
        val t = (minOf(y0, y1) * sy).toInt().coerceIn(0, bmp.height - 1)
        val r = (maxOf(x0, x1) * sx).toInt().coerceIn(0, bmp.width)
        val b = (maxOf(y0, y1) * sy).toInt().coerceIn(0, bmp.height)
        if (r - l < 12 || b - t < 8) return null
        return CapResult(bmp, l, t, Bitmap.createBitmap(bmp, l, t, r - l, b - t))
    }
}

class BotService : AccessibilityService() {
    companion object {
        @Volatile var inst: BotService? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private var wm: WindowManager? = null
    val engine = BotEngine(this)
    private var panel: LinearLayout? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var statusTv: TextView? = null
    private var playIcon: IconView? = null
    private var banner: View? = null
    private var pickLayer: View? = null
    val rec = RouteRecorder(this)
    private var recBanner: View? = null
    private var recTv: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        inst = this
        Store.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        engine.stop()
        rec.finish(false)
        ui.post { removeAll() }
        inst = null
        return super.onUnbind(intent)
    }

    // ---------- util ----------
    fun toast(s: String) {
        ui.post { Toast.makeText(this@BotService, s, Toast.LENGTH_LONG).show() }
    }

    fun setStatus(t: String) {
        ui.post { statusTv?.text = t }
    }

    /** Aviso forte: mensagem na tela + celular vibrando (boss, vida crítica, tela de morte). */
    fun alert(msg: String) {
        toast(msg)
        setStatus(msg)
        try {
            val pattern = longArrayOf(0, 500, 250, 500, 250, 900)
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    v.vibrate(pattern, -1)
                }
            }
        } catch (e: Throwable) { }
    }

    fun runState(on: Boolean) {
        ui.post {
            playIcon?.kind = if (on) IconView.STOP else IconView.PLAY
            playIcon?.color = if (on) Pal.BAD else Pal.OK
            playIcon?.invalidate()
            if (!on && statusTv?.text?.toString()?.startsWith("Rodando") == true) statusTv?.text = "Parado"
        }
    }

    private fun overlayLp(w: Int, h: Int, flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)

    private fun removeView(v: View?) {
        if (v == null) return
        try { wm?.removeView(v) } catch (e: Exception) { }
    }

    private fun removeAll() {
        removeView(panel); panel = null
        closeBanner(); closePick(); closeRec()
    }

    private fun closeBanner() { removeView(banner); banner = null }
    private fun closePick() { removeView(pickLayer); pickLayer = null }
    private fun closeRec() { removeView(recBanner); recBanner = null; recTv = null }

    fun recStatus(t: String) {
        ui.post { recTv?.text = t }
    }

    /** Abre o painel de gravação de rota. O jogador anda no jogo e o app mede o caminho. */
    fun startRecording() {
        ui.post {
            val w = wm ?: return@post
            if (engine.running) { toast("Pare o bot antes de gravar a rota."); return@post }
            if (rec.active) return@post
            if (Build.VERSION.SDK_INT < 30) { toast("Gravar rota precisa de Android 11 ou mais novo."); return@post }
            closeBanner(); closePick(); closeRec()
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(14), dp(12), dp(14), dp(12))
            box.background = rounded(Color.parseColor("#F21A1F36"), dp(18).toFloat(), Pal.BAD, dp(1))
            val t = text("Gravando… ande pelo jogo.", 13f)
            t.maxWidth = dp(300)
            box.addView(t)
            recTv = t
            val b0 = LinearLayout.LayoutParams(MP, WC)
            b0.topMargin = dp(10)
            box.addView(button("📍 Marcar ponto de caça", Pal.ACC) {
                rec.mark()
                toast("Ponto de caça marcado aqui.")
            }, b0)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val b1 = LinearLayout.LayoutParams(0, WC, 1f)
            b1.topMargin = dp(8); b1.rightMargin = dp(6)
            val b2 = LinearLayout.LayoutParams(0, WC, 1f)
            b2.topMargin = dp(8); b2.leftMargin = dp(6)
            row.addView(button("Salvar", Pal.OK) { finishRec(true) }, b1)
            row.addView(button("Cancelar", Pal.BAD) { finishRec(false) }, b2)
            box.addView(row, LinearLayout.LayoutParams(MP, WC))
            val lp = overlayLp(WC, WC, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.y = dp(36)
            try { w.addView(box, lp) } catch (e: Exception) { return@post }
            recBanner = box
            rec.start()
        }
    }

    fun finishRec(save: Boolean) {
        ui.post {
            if (save && rec.count() < 2) {
                toast("Rota muito curta: ande mais um pouco antes de salvar.")
                return@post
            }
            rec.finish(save)
            closeRec()
            if (save) openApp()
        }
    }

    fun openApp() {
        val i = Intent(this, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { startActivity(i) } catch (e: Exception) { }
    }

    // ---------- tela e toques ----------
    fun shot(): Bitmap? {
        if (Build.VERSION.SDK_INT < 30) return null
        val latch = CountDownLatch(1)
        var out: Bitmap? = null
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, exec, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    try {
                        val hb = r.hardwareBuffer
                        val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                        out = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        hb.close()
                    } catch (e: Throwable) {
                    } finally {
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) { latch.countDown() }
            })
        } catch (e: Throwable) {
            return null
        }
        latch.await(4, TimeUnit.SECONDS)
        return out
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        val latch = CountDownLatch(1)
        var ok = false
        val sent = dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
        }, null)
        if (!sent) return false
        latch.await(2, TimeUnit.SECONDS)
        return ok
    }

    // ---------- painel flutuante ----------
    fun toggleBot() {
        if (engine.running) {
            engine.stop()
        } else {
            if (rec.active) {
                toast("Termine a gravação da rota primeiro (Salvar ou Cancelar).")
                return
            }
            if (Store.cfg.targets.none { !it.isItem && !it.alert && it.enabled && it.ready() }) {
                toast("Capture pelo menos 1 monstro na aba Alvos do app.")
                return
            }
            engine.start()
        }
    }

    fun showPanel() {
        ui.post {
            val w = wm ?: return@post
            val existing = panel
            if (existing != null) { existing.visibility = View.VISIBLE; return@post }
            val box = LinearLayout(this)
            box.orientation = LinearLayout.HORIZONTAL
            box.gravity = Gravity.CENTER_VERTICAL
            box.setPadding(dp(8), dp(6), dp(10), dp(6))
            box.background = rounded(Color.parseColor("#EB1A1F36"), dp(28).toFloat(), Pal.ACC, dp(1))
            val play = IconView(this, IconView.PLAY, Pal.OK)
            play.setOnClickListener { toggleBot() }
            box.addView(play, LinearLayout.LayoutParams(dp(40), dp(40)))
            val st = text("Parado", 12f)
            st.maxWidth = dp(170)
            st.setPadding(dp(8), 0, dp(8), 0)
            box.addView(st)
            val home = IconView(this, IconView.HOME, Pal.TXT)
            home.setOnClickListener { openApp() }
            box.addView(home, LinearLayout.LayoutParams(dp(34), dp(34)))
            val x = IconView(this, IconView.CLOSE, Pal.SUB)
            x.setOnClickListener {
                engine.stop()
                panel?.visibility = View.GONE
            }
            box.addView(x, LinearLayout.LayoutParams(dp(34), dp(34)))

            val lp = overlayLp(WC, WC, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = dp(8)
            lp.y = dp(80)
            var sx = 0f
            var sy = 0f
            var ox = 0
            var oy = 0
            box.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = ox + (e.rawX - sx).toInt()
                        lp.y = oy + (e.rawY - sy).toInt()
                        try { w.updateViewLayout(box, lp) } catch (ex: Exception) { }
                    }
                }
                true
            }
            try { w.addView(box, lp) } catch (e: Exception) { return@post }
            panel = box
            panelLp = lp
            statusTv = st
            playIcon = play
        }
    }

    // ---------- captura (ponto ou região) ----------
    fun startCapture(bannerText: String, pickText: String, region: Boolean, line: Boolean = false, done: (CapResult) -> Unit) {
        ui.post {
            val w = wm ?: return@post
            if (engine.running) { toast("Pare o bot antes de configurar."); return@post }
            closeBanner(); closePick()
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(14), dp(12), dp(14), dp(12))
            box.background = rounded(Color.parseColor("#F21A1F36"), dp(18).toFloat(), Pal.ACC, dp(1))
            val t = text(bannerText, 13f)
            t.maxWidth = dp(300)
            box.addView(t)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val b1 = LinearLayout.LayoutParams(0, WC, 1f)
            b1.topMargin = dp(10); b1.rightMargin = dp(6)
            val b2 = LinearLayout.LayoutParams(0, WC, 1f)
            b2.topMargin = dp(10); b2.leftMargin = dp(6)
            row.addView(button("Capturar agora") { grab(pickText, region, line, done) }, b1)
            row.addView(button("Cancelar", Pal.BAD) { closeBanner() }, b2)
            box.addView(row, LinearLayout.LayoutParams(MP, WC))
            val lp = overlayLp(WC, WC, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.y = dp(36)
            try { w.addView(box, lp) } catch (e: Exception) { return@post }
            banner = box
        }
    }

    private fun grab(pickText: String, region: Boolean, line: Boolean, done: (CapResult) -> Unit) {
        closeBanner()
        val old = panel?.visibility ?: View.VISIBLE
        panel?.visibility = View.INVISIBLE
        ui.postDelayed({
            Thread {
                val bmp = shot()
                ui.post {
                    panel?.visibility = old
                    if (bmp == null) {
                        toast("Não consegui capturar a tela. Precisa do Android 11+ e o jogo não pode bloquear capturas.")
                    } else {
                        showPick(pickText, region, line, bmp, done)
                    }
                }
            }.start()
        }, 400)
    }

    private fun showPick(pickText: String, region: Boolean, line: Boolean, bmp: Bitmap, done: (CapResult) -> Unit) {
        val w = wm ?: return
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        w.defaultDisplay.getRealMetrics(dm)
        val root = FrameLayout(this)
        val pv = PickView(this, bmp, region, line)
        root.addView(pv, FrameLayout.LayoutParams(MP, MP))
        val tip = text(pickText, 14f)
        tip.setPadding(dp(14), dp(10), dp(14), dp(10))
        tip.background = rounded(Color.parseColor("#F21A1F36"), dp(14).toFloat(), Pal.OK, dp(1))
        tip.maxWidth = (dm.widthPixels * 0.8f).toInt()
        val tlp = FrameLayout.LayoutParams(WC, WC, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        tlp.topMargin = dp(24)
        root.addView(tip, tlp)
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        val c1 = LinearLayout.LayoutParams(dp(140), WC)
        c1.rightMargin = dp(8)
        val c2 = LinearLayout.LayoutParams(dp(140), WC)
        c2.leftMargin = dp(8)
        bar.addView(button("Cancelar", Pal.BAD) { closePick() }, c1)
        bar.addView(button("Confirmar", Pal.OK) {
            val sel = pv.selection()
            if (sel == null) {
                toast(if (region) "Arraste um retângulo sobre a imagem." else if (line) "Arraste uma linha ao longo da barra." else "Toque no ponto desejado.")
            } else {
                closePick()
                try { done(sel) } catch (e: Exception) { toast("Erro ao salvar: ${e.message}") }
                toast("Salvo! Voltando para o app…")
                openApp()
            }
        }, c2)
        val blp = FrameLayout.LayoutParams(WC, WC, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        blp.bottomMargin = dp(24)
        root.addView(bar, blp)
        val lp = overlayLp(
            dm.widthPixels, dm.heightPixels,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        try { w.addView(root, lp) } catch (e: Exception) { return }
        pickLayer = root
    }
}
