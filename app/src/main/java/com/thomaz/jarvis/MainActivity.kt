package com.thomaz.jarvis

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var orb: View
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var rolagem: ScrollView
    private lateinit var botao: Button
    private var pulsando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        montarTela()
        for (l in JarvisService.linhas) log.append(l + "\n\n")
        if (chave().isEmpty()) pedirChave()
    }

    override fun onResume() {
        super.onResume()
        JarvisService.aoMudar = { s, p ->
            status.text = s
            pulsar(p)
        }
        JarvisService.aoLogar = { l -> addLog(l) }
        if (JarvisService.rodando) {
            botao.text = "DESATIVAR JARVIS"
            if (JarvisService.ultimoStatus.isNotEmpty()) status.text = JarvisService.ultimoStatus
        } else {
            botao.text = "ATIVAR JARVIS"
            status.text = "Toque em ATIVAR JARVIS"
        }
    }

    override fun onPause() {
        JarvisService.aoMudar = null
        JarvisService.aoLogar = null
        pulsando = false
        super.onPause()
    }

    private fun montarTela() {
        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.gravity = Gravity.CENTER_HORIZONTAL
        raiz.setBackgroundColor(Color.parseColor("#02070D"))
        raiz.setPadding(40, 100, 40, 40)

        val titulo = TextView(this)
        titulo.text = "J.A.R.V.I.S."
        titulo.setTextColor(Color.parseColor("#00E5FF"))
        titulo.textSize = 28f
        titulo.letterSpacing = 0.3f
        titulo.setOnLongClickListener {
            pedirChave()
            true
        }
        raiz.addView(titulo)

        val orbe = FrameLayout(this)
        val anel = GradientDrawable()
        anel.shape = GradientDrawable.OVAL
        anel.setColor(Color.parseColor("#1500E5FF"))
        anel.setStroke(10, Color.parseColor("#00E5FF"))
        orbe.background = anel
        val nucleo = View(this)
        val formaNucleo = GradientDrawable()
        formaNucleo.shape = GradientDrawable.OVAL
        formaNucleo.setColor(Color.parseColor("#6600E5FF"))
        nucleo.background = formaNucleo
        orbe.addView(nucleo, FrameLayout.LayoutParams(150, 150, Gravity.CENTER))
        val lpOrb = LinearLayout.LayoutParams(380, 380)
        lpOrb.topMargin = 70
        lpOrb.bottomMargin = 40
        orbe.layoutParams = lpOrb
        orbe.setOnClickListener { tocarOrbe() }
        orb = orbe
        raiz.addView(orbe)

        status = TextView(this)
        status.text = "Toque em ATIVAR JARVIS"
        status.setTextColor(Color.parseColor("#7FDFFF"))
        status.textSize = 16f
        raiz.addView(status)

        rolagem = ScrollView(this)
        log = TextView(this)
        log.setTextColor(Color.parseColor("#CFEFFF"))
        log.textSize = 15f
        log.setPadding(10, 30, 10, 10)
        rolagem.addView(log)
        raiz.addView(rolagem, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        botao = Button(this)
        botao.text = "ATIVAR JARVIS"
        botao.setOnClickListener {
            if (JarvisService.rodando) desativar() else ativar()
        }
        raiz.addView(botao, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(raiz)
    }

    private fun prefs() = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    private fun chave(): String = prefs().getString("chave", "") ?: ""

    private fun pedirChave() {
        val campo = EditText(this)
        campo.hint = "Cole a chave do Gemini"
        AlertDialog.Builder(this)
            .setTitle("Chave do Gemini")
            .setView(campo)
            .setPositiveButton("Salvar") { _, _ ->
                prefs().edit().putString("chave", campo.text.toString().trim()).apply()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun tocarOrbe() {
        if (JarvisService.rodando) {
            startService(Intent(this, JarvisService::class.java).setAction(JarvisService.ACAO_OUVIR))
        } else {
            ativar()
        }
    }

    private fun ativar() {
        if (chave().isEmpty()) {
            pedirChave()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            status.text = "Ative \"Sobrepor a outros apps\" para o Jarvis, volte e toque de novo"
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs().getBoolean("bateria_pedida", false)) {
            prefs().edit().putBoolean("bateria_pedida", true).apply()
            status.text = "Permita rodar sem restrição de bateria, volte e toque de novo"
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName)))
            return
        }
        startForegroundService(Intent(this, JarvisService::class.java))
        botao.text = "DESATIVAR JARVIS"
        status.text = "Iniciando..."
    }

    private fun desativar() {
        startService(Intent(this, JarvisService::class.java).setAction(JarvisService.ACAO_PARAR))
        botao.text = "ATIVAR JARVIS"
        pulsar(false)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) ativar()
    }

    private fun pulsar(ligar: Boolean) {
        val estava = pulsando
        pulsando = ligar
        if (ligar && !estava) pulso()
    }

    private fun pulso() {
        if (!pulsando) {
            orb.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            return
        }
        orb.animate().scaleX(1.15f).scaleY(1.15f).setDuration(450).withEndAction {
            orb.animate().scaleX(1f).scaleY(1f).setDuration(450).withEndAction { pulso() }.start()
        }.start()
    }

    private fun addLog(t: String) {
        log.append(t + "\n\n")
        rolagem.post { rolagem.fullScroll(View.FOCUS_DOWN) }
    }
}
