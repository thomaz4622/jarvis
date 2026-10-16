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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val sistema = "Você é o J.A.R.V.I.S., assistente pessoal do Tom. Responda sempre em português do Brasil, de forma curta (no máximo 3 frases), educada e elegante, como no filme Homem de Ferro. Não use markdown, asteriscos nem emojis, pois sua resposta será falada em voz alta. Pedidos para abrir aplicativos ou pesquisar são tratados por outra parte do sistema: se algo assim chegar até você, diga que não entendeu o comando e peça para repetir de forma simples, por exemplo: abrir WhatsApp."

    private lateinit var tts: TextToSpeech
    private var ttsPronto = false
    private var reconhecedor: SpeechRecognizer? = null
    private lateinit var orb: View
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var rolagem: ScrollView
    private val ui = Handler(Looper.getMainLooper())
    private val historico = JSONArray()
    private var pulsando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        carregarHistorico()
        montarTela()
        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                tts.language = Locale("pt", "BR")
                ttsPronto = true
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        ui.post { pulsar(true); status.text = "Falando..." }
                    }
                    override fun onDone(id: String?) {
                        ui.post { pulsar(false); status.text = "Toque no círculo e fale" }
                    }
                    override fun onError(id: String?) {
                        ui.post { pulsar(false); status.text = "Toque no círculo e fale" }
                    }
                })
                falar("Sistema online. Em que posso ajudar, senhor?")
            }
        }
        if (chave().isEmpty()) pedirChave()
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
        titulo.setOnLongClickListener { pedirChave(); true }
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
        orbe.setOnClickListener { ouvir() }
        orb = orbe
        raiz.addView(orbe)

        status = TextView(this)
        status.text = "Toque no círculo e fale"
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

        val botao = Button(this)
        botao.text = "FALAR"
        botao.setOnClickListener { ouvir() }
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

    private fun carregarHistorico() {
        try {
            val s = prefs().getString("hist", null) ?: return
            val a = JSONArray(s)
            for (i in 0 until a.length()) historico.put(a.get(i))
        } catch (e: Exception) {
        }
    }

    private fun salvarHistorico() {
        prefs().edit().putString("hist", historico.toString()).apply()
    }

    private fun ouvir() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (chave().isEmpty()) {
            pedirChave()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Reconhecimento de voz indisponível neste aparelho"
            return
        }
        if (ttsPronto) tts.stop()
        reconhecedor?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        reconhecedor = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "Ouvindo..."
                pulsar(true)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                pulsar(false)
                status.text = "Não entendi. Toque no círculo e tente de novo"
            }
            override fun onResults(results: Bundle?) {
                pulsar(false)
                val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (texto.isNullOrBlank()) {
                    status.text = "Não entendi. Toque no círculo e tente de novo"
                    return
                }
                responder(texto)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        r.startListening(i)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) ouvir()
    }

    private fun responder(texto: String) {
        addLog("Você: $texto")
        val local = comandoLocal(texto)
        if (local != null) {
            addLog("Jarvis: $local")
            falar(local)
            return
        }
        status.text = "Pensando..."
        pulsar(true)
        thread {
            val resp = perguntar(texto)
            ui.post {
                addLog("Jarvis: $resp")
                falar(resp)
            }
        }
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    private fun comandoLocal(original: String): String? {
        var t = norm(original).replace(Regex("[,.!?]"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        t = t.replace(Regex("^((jarvis|por favor|ok|oi|ola|boa noite|bom dia|boa tarde|entao|pode|poderia|voce pode) )+"), "").trim()

        if ((t.contains("que horas") || t.contains("as horas") || t.contains("hora e agora") || t.contains("hora atual")) && !t.contains("quantas")) {
            val c = Calendar.getInstance()
            val h = c.get(Calendar.HOUR_OF_DAY)
            val m = c.get(Calendar.MINUTE)
            val horas = when (h) {
                0 -> "É meia-noite"
                1 -> "É 1 hora"
                12 -> "É meio-dia"
                else -> "São $h horas"
            }
            val minutos = when (m) {
                0 -> " em ponto"
                1 -> " e 1 minuto"
                else -> " e $m minutos"
            }
            return "$horas$minutos, senhor."
        }

        if (t.contains("que dia") || t.contains("data de hoje") || t.contains("dia e hoje")) {
            val f = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
            return "Hoje é ${f.format(Date())}, senhor."
        }

        val verbo = Regex("^(abr\\w*|inici\\w*|execut\\w*|toc\\w*|pesquis\\w*|busc\\w*|procur\\w*|mostr\\w*|coloc\\w*)\\b")
        if (!verbo.containsMatchIn(t)) return null

        if (t.contains("youtube")) {
            val semYt = t.replace("no youtube", " ").replace("youtube", " ").replace(Regex("\\s+"), " ").trim()
            val m = Regex("(?:videos?|busc\\w*|pesquis\\w*|procur\\w*|toc\\w+|mostr\\w*)\\s+(?:de |do |da |sobre |por )?(.+)").find(semYt)
            val consulta = m?.groupValues?.get(1)?.replace(Regex("\\bpor favor\\b"), "")?.trim() ?: ""
            if (consulta.isNotEmpty()) {
                abrirUrl("https://www.youtube.com/results?search_query=" + Uri.encode(consulta), "com.google.android.youtube")
                return "Buscando $consulta no YouTube, senhor."
            }
            return if (abrirApp("youtube")) "Abrindo o YouTube, senhor." else "Não consegui abrir o YouTube, senhor."
        }

        val pesq = Regex("^(?:pesquis\\w*|busc\\w*|procur\\w*)\\s+(?:no google |na internet |por |sobre |de )*(.+)").find(t)
        if (pesq != null) {
            val q = pesq.groupValues[1].replace(Regex("\\bpor favor\\b"), "").trim()
            if (q.isNotEmpty()) {
                abrirUrl("https://www.google.com/search?q=" + Uri.encode(q), null)
                return "Pesquisando $q, senhor."
            }
        }

        val abrir = Regex("^(?:abr\\w*|inici\\w*|execut\\w*)\\s+(?:(?:o|a|os|as|meu|minha|aplicativo|app) )*(.+)").find(t)
        if (abrir != null) {
            val nome = abrir.groupValues[1]
                .replace(Regex("\\b(por favor|pra mim|para mim|aplicativo|app)\\b"), "")
                .replace(Regex("\\s+"), " ").trim()
            if (nome.isNotEmpty()) {
                return if (abrirApp(nome)) "Abrindo $nome, senhor." else "Não encontrei o aplicativo $nome, senhor."
            }
        }
        return null
    }

    private fun abrirUrl(url: String, pacote: String?) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pacote != null) i.setPackage(pacote)
        try {
            startActivity(i)
        } catch (e: Exception) {
            i.setPackage(null)
            try {
                startActivity(i)
            } catch (e2: Exception) {
            }
        }
    }

    private fun abrirApp(nome: String): Boolean {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val lista = pm.queryIntentActivities(intent, 0)
        var melhor: String? = null
        var nota = 0
        for (ri in lista) {
            val rotulo = norm(ri.loadLabel(pm).toString())
            val n = if (rotulo == nome) 3
            else if (rotulo.startsWith(nome)) 2
            else if (rotulo.contains(nome) || (rotulo.length >= 3 && nome.contains(rotulo))) 1
            else 0
            if (n > nota) {
                nota = n
                melhor = ri.activityInfo.packageName
            }
        }
        if (melhor == null) return false
        val li = pm.getLaunchIntentForPackage(melhor) ?: return false
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(li)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun sistemaAgora(): String {
        val f = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", Locale("pt", "BR"))
        return sistema + " Data e hora atuais do usuário: " + f.format(Date()) + "."
    }

    private fun perguntar(texto: String): String {
        val turnoUser = JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", texto)))
        val conteudo = JSONArray()
        for (i in 0 until historico.length()) conteudo.put(historico.get(i))
        conteudo.put(turnoUser)
        val corpo = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sistemaAgora()))))
            .put("contents", conteudo)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=" + chave()
        var erro = ""
        for (t in 0 until 3) {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.setRequestProperty("Content-Type", "application/json")
                c.connectTimeout = 15000
                c.readTimeout = 40000
                c.doOutput = true
                c.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }
                val codigo = c.responseCode
                if (codigo == 200) {
                    val txt = c.inputStream.bufferedReader().use { it.readText() }
                    val resposta = JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                        .getString("text").trim()
                    historico.put(turnoUser)
                    historico.put(JSONObject().put("role", "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", resposta))))
                    while (historico.length() > 30) {
                        historico.remove(0)
                        historico.remove(0)
                    }
                    salvarHistorico()
                    return resposta
                } else if (codigo == 429) {
                    return "O limite gratuito do Google foi atingido, senhor. Aguarde um minuto e tente novamente."
                } else if (codigo == 400 || codigo == 401 || codigo == 403 || codigo == 404) {
                    return "Há um problema com a chave ou o modelo, senhor (erro $codigo). Segure o título por alguns segundos para trocar a chave."
                } else {
                    erro = "erro $codigo"
                    Thread.sleep(2000L * (t + 1))
                }
            } catch (e: Exception) {
                erro = e.message ?: "falha"
                Thread.sleep(2000L * (t + 1))
            }
        }
        return "Não consegui falar com o servidor, senhor ($erro). Tente de novo em instantes."
    }

    private fun falar(texto: String) {
        val limpo = texto.replace("*", "").replace("#", "").replace("_", " ")
        if (!ttsPronto) {
            pulsar(false)
            status.text = "Toque no círculo e fale"
            return
        }
        tts.speak(limpo, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
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

    override fun onDestroy() {
        reconhecedor?.destroy()
        try {
            tts.stop()
            tts.shutdown()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
