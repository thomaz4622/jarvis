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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val ciano = Color.parseColor("#00E5FF")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var reator: ReatorView
    private lateinit var status: TextView
    private lateinit var lista: LinearLayout
    private lateinit var rolagem: ScrollView
    private lateinit var botao: Button
    private lateinit var paginaJarvis: LinearLayout
    private lateinit var paginaNoticias: LinearLayout
    private lateinit var tabJarvis: TextView
    private lateinit var tabNoticias: TextView
    private lateinit var listaNoticias: LinearLayout
    private lateinit var statusNoticias: TextView
    private var noticiasCarregadas = false
    private var vistas = 0

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun forma(cor: Int, raio: Int, borda: Int, corBorda: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(cor)
        g.cornerRadius = dp(raio).toFloat()
        if (borda > 0) g.setStroke(dp(borda), corBorda)
        return g
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = Color.parseColor("#050A12")
        window.navigationBarColor = Color.parseColor("#08111D")
        montarTela()
        sincronizarLog()
        if (chave("chave").isEmpty()) pedirChave("Chave do Gemini", "chave")
    }

    override fun onResume() {
        super.onResume()
        JarvisService.aoMudar = { s, m ->
            status.text = s
            reator.modo = m
        }
        JarvisService.aoLogar = { l ->
            addLog(l)
            vistas = JarvisService.totalLinhas
        }
        sincronizarLog()
        if (JarvisService.rodando) {
            botao.text = "DESATIVAR JARVIS"
            if (JarvisService.ultimoStatus.isNotEmpty()) status.text = JarvisService.ultimoStatus
            reator.modo = JarvisService.ultimoModo
        } else {
            botao.text = "ATIVAR JARVIS"
            status.text = "Toque em ATIVAR JARVIS"
            reator.modo = 0
        }
    }

    override fun onPause() {
        JarvisService.aoMudar = null
        JarvisService.aoLogar = null
        super.onPause()
    }

    private fun montarTela() {
        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#050A12"), Color.parseColor("#0A1626"))
        )

        paginaJarvis = LinearLayout(this)
        paginaJarvis.orientation = LinearLayout.VERTICAL
        paginaJarvis.gravity = Gravity.CENTER_HORIZONTAL
        paginaJarvis.setPadding(dp(16), dp(16), dp(16), dp(12))

        val titulo = TextView(this)
        titulo.text = "J.A.R.V.I.S."
        titulo.setTextColor(ciano)
        titulo.textSize = 26f
        titulo.letterSpacing = 0.35f
        titulo.setOnLongClickListener {
            escolherChave()
            true
        }
        paginaJarvis.addView(titulo)

        val sub = TextView(this)
        sub.text = "JUST A RATHER VERY INTELLIGENT SYSTEM"
        sub.setTextColor(Color.parseColor("#4F8FA6"))
        sub.textSize = 9f
        sub.letterSpacing = 0.2f
        paginaJarvis.addView(sub)

        reator = ReatorView(this)
        val lpR = LinearLayout.LayoutParams(dp(250), dp(250))
        lpR.topMargin = dp(14)
        lpR.bottomMargin = dp(8)
        reator.layoutParams = lpR
        reator.setOnClickListener { tocarOrbe() }
        paginaJarvis.addView(reator)

        status = TextView(this)
        status.text = "Toque em ATIVAR JARVIS"
        status.setTextColor(Color.parseColor("#7FDFFF"))
        status.textSize = 14f
        status.gravity = Gravity.CENTER
        paginaJarvis.addView(status)

        rolagem = ScrollView(this)
        lista = LinearLayout(this)
        lista.orientation = LinearLayout.VERTICAL
        rolagem.addView(lista)
        val lpS = LinearLayout.LayoutParams(MATCH, 0, 1f)
        lpS.topMargin = dp(10)
        paginaJarvis.addView(rolagem, lpS)

        botao = Button(this)
        botao.text = "ATIVAR JARVIS"
        botao.setTextColor(ciano)
        botao.background = forma(Color.parseColor("#0A2230"), 26, 2, ciano)
        botao.stateListAnimator = null
        botao.setOnClickListener {
            if (JarvisService.rodando) desativar() else ativar()
        }
        val lpB = LinearLayout.LayoutParams(MATCH, dp(52))
        lpB.topMargin = dp(10)
        paginaJarvis.addView(botao, lpB)

        raiz.addView(paginaJarvis, LinearLayout.LayoutParams(MATCH, 0, 1f))

        paginaNoticias = LinearLayout(this)
        paginaNoticias.orientation = LinearLayout.VERTICAL
        paginaNoticias.setPadding(dp(16), dp(16), dp(16), dp(12))
        paginaNoticias.visibility = View.GONE

        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.HORIZONTAL
        cab.gravity = Gravity.CENTER_VERTICAL
        val tn = TextView(this)
        tn.text = "MANCHETES"
        tn.setTextColor(ciano)
        tn.textSize = 22f
        tn.letterSpacing = 0.25f
        cab.addView(tn, LinearLayout.LayoutParams(0, WRAP, 1f))
        val atualizar = Button(this)
        atualizar.text = "ATUALIZAR"
        atualizar.textSize = 12f
        atualizar.setTextColor(ciano)
        atualizar.background = forma(Color.parseColor("#0A2230"), 20, 1, ciano)
        atualizar.stateListAnimator = null
        atualizar.setOnClickListener { carregarNoticias() }
        cab.addView(atualizar, LinearLayout.LayoutParams(WRAP, dp(40)))
        paginaNoticias.addView(cab)

        statusNoticias = TextView(this)
        statusNoticias.text = "Toque em ATUALIZAR"
        statusNoticias.setTextColor(Color.parseColor("#4F8FA6"))
        statusNoticias.textSize = 12f
        statusNoticias.setPadding(0, dp(8), 0, dp(4))
        paginaNoticias.addView(statusNoticias)

        val rolN = ScrollView(this)
        listaNoticias = LinearLayout(this)
        listaNoticias.orientation = LinearLayout.VERTICAL
        rolN.addView(listaNoticias)
        paginaNoticias.addView(rolN, LinearLayout.LayoutParams(MATCH, 0, 1f))
        raiz.addView(paginaNoticias, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val barra = LinearLayout(this)
        barra.orientation = LinearLayout.HORIZONTAL
        barra.setBackgroundColor(Color.parseColor("#08111D"))
        tabJarvis = criarTab("JARVIS")
        tabNoticias = criarTab("NOTÍCIAS")
        tabJarvis.setOnClickListener { mostrar(0) }
        tabNoticias.setOnClickListener { mostrar(1) }
        barra.addView(tabJarvis, LinearLayout.LayoutParams(0, WRAP, 1f))
        barra.addView(tabNoticias, LinearLayout.LayoutParams(0, WRAP, 1f))
        raiz.addView(barra, LinearLayout.LayoutParams(MATCH, WRAP))

        setContentView(raiz)
        mostrar(0)
    }

    private fun criarTab(texto: String): TextView {
        val t = TextView(this)
        t.text = texto
        t.textSize = 12f
        t.letterSpacing = 0.2f
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(16), 0, dp(16))
        return t
    }

    private fun mostrar(aba: Int) {
        paginaJarvis.visibility = if (aba == 0) View.VISIBLE else View.GONE
        paginaNoticias.visibility = if (aba == 1) View.VISIBLE else View.GONE
        tabJarvis.setTextColor(if (aba == 0) ciano else Color.parseColor("#4F6F7F"))
        tabNoticias.setTextColor(if (aba == 1) ciano else Color.parseColor("#4F6F7F"))
        if (aba == 1 && !noticiasCarregadas) carregarNoticias()
    }

    private fun carregarNoticias() {
        statusNoticias.text = "Buscando manchetes..."
        thread {
            val itens: List<Noticias.Item> = try {
                Noticias.buscar()
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                listaNoticias.removeAllViews()
                if (itens.isEmpty()) {
                    statusNoticias.text = "Não consegui carregar. Verifique a internet e toque em ATUALIZAR."
                } else {
                    statusNoticias.text = "Toque numa manchete para abrir"
                    noticiasCarregadas = true
                    for (n in itens) listaNoticias.addView(cartao(n))
                }
            }
        }
    }

    private fun cartao(n: Noticias.Item): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(12), dp(16), dp(12))
        c.background = forma(Color.parseColor("#0E1A2B"), 16, 1, Color.parseColor("#123049"))
        val f = TextView(this)
        f.text = n.fonte.uppercase()
        f.setTextColor(ciano)
        f.textSize = 11f
        f.letterSpacing = 0.15f
        val t = TextView(this)
        t.text = n.titulo
        t.setTextColor(Color.WHITE)
        t.textSize = 16f
        c.addView(f)
        c.addView(t)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(10)
        c.layoutParams = lp
        c.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link)))
            } catch (e: Exception) {
            }
        }
        return c
    }

    private fun sincronizarLog() {
        val novas = JarvisService.totalLinhas - vistas
        if (novas > 0) {
            val n = minOf(novas, JarvisService.linhas.size)
            val ini = JarvisService.linhas.size - n
            for (i in ini until JarvisService.linhas.size) addLog(JarvisService.linhas[i])
            vistas = JarvisService.totalLinhas
        }
    }

    private fun addLog(t: String) {
        val usuario = t.startsWith("Você:")
        val v = TextView(this)
        v.text = t.substringAfter(": ")
        v.setTextColor(Color.parseColor("#E8F9FF"))
        v.textSize = 15f
        v.setPadding(dp(14), dp(10), dp(14), dp(10))
        v.background = if (usuario) forma(Color.parseColor("#12394F"), 18, 1, Color.parseColor("#1D6A8A"))
        else forma(Color.parseColor("#0E1A2B"), 18, 1, Color.parseColor("#123049"))
        v.maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.gravity = if (usuario) Gravity.END else Gravity.START
        lp.topMargin = dp(6)
        v.layoutParams = lp
        lista.addView(v)
        rolagem.post { rolagem.fullScroll(View.FOCUS_DOWN) }
    }

    private fun prefs() = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    private fun chave(nome: String): String = prefs().getString(nome, "") ?: ""

    private fun pedirChave(titulo: String, pref: String) {
        val campo = EditText(this)
        campo.hint = "Cole a chave aqui"
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setView(campo)
            .setPositiveButton("Salvar") { _, _ ->
                prefs().edit().putString(pref, campo.text.toString().trim()).apply()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun escolherChave() {
        AlertDialog.Builder(this)
            .setTitle("Configurações")
            .setItems(arrayOf("Chave do Gemini", "Chave da Picovoice (palavra Jarvis)", "Voz do Gemini: ligar/desligar")) { _, quem ->
                if (quem == 0) {
                    pedirChave("Chave do Gemini", "chave")
                } else if (quem == 1) {
                    pedirChave("Chave da Picovoice", "chave_pv")
                } else {
                    val liga = !prefs().getBoolean("voz_gemini", true)
                    prefs().edit().putBoolean("voz_gemini", liga).apply()
                    Toast.makeText(this, if (liga) "Voz do Gemini ligada" else "Voz do celular ligada", Toast.LENGTH_SHORT).show()
                }
            }
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
        if (chave("chave").isEmpty()) {
            pedirChave("Chave do Gemini", "chave")
            return
        }
        if (chave("chave_pv").isEmpty()) {
            status.text = "Crie a chave grátis em console.picovoice.ai e cole aqui"
            pedirChave("Chave da Picovoice", "chave_pv")
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
        reator.modo = 0
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) ativar()
    }
}
