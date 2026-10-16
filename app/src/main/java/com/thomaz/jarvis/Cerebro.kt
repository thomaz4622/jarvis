package com.thomaz.jarvis

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Cerebro(private val ctx: Context) {

    private val instrucoes = "Você é o J.A.R.V.I.S., assistente pessoal do Tom. Trate-o por senhor. " +
        "Responda SEMPRE com um único objeto JSON com os campos acao, alvo e fala. " +
        "acao deve ser exatamente uma de: conversar, abrir_app, youtube, pesquisar, alarme, timer, lanterna_ligar, lanterna_desligar. " +
        "alvo: nome do aplicativo para abrir_app; assunto do vídeo para youtube (vazio apenas para abrir o YouTube); texto da busca para pesquisar; horário HH:MM em 24 horas para alarme; número de segundos para timer; vazio nos demais casos. " +
        "fala: o que será dito em voz alta, em português do Brasil, curto (até 3 frases), educado e elegante, sem markdown nem emojis. Quando executar uma ação, fala é uma confirmação breve. " +
        "Se o pedido não for uma dessas ações, use acao conversar e responda normalmente em fala."

    private val historico = JSONArray()

    init {
        try {
            val s = prefs().getString("hist2", null)
            if (s != null) {
                val a = JSONArray(s)
                for (i in 0 until a.length()) historico.put(a.get(i))
            }
        } catch (e: Exception) {
        }
    }

    private fun prefs() = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    private fun chave(): String = prefs().getString("chave", "") ?: ""

    private fun memorias(): JSONArray {
        return try {
            JSONArray(prefs().getString("memorias", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun salvarMemorias(a: JSONArray) {
        prefs().edit().putString("memorias", a.toString()).apply()
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    private fun limpar(s: String): String {
        var t = norm(s).replace(Regex("[,.!?]"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        t = t.replace(Regex("^((jarvis|por favor|ok|oi|ola|boa noite|bom dia|boa tarde|entao|pode|poderia|voce pode) )+"), "").trim()
        return t
    }

    fun processar(original: String): String {
        val t = limpar(original)
        val sem = original.trim().replace(Regex("^(por favor[, ]+)+", RegexOption.IGNORE_CASE), "")

        val mem = Regex("^(?:me\\s+)?(?:lembr\\w*|anot\\w*|guard\\w*|memoriz\\w*)(?:-se)?\\s+(?:que|de|isso|isto|o seguinte)[:,]?\\s+(.+)", RegexOption.IGNORE_CASE).find(sem)
        if (mem != null) {
            val item = mem.groupValues[1].trim().trimEnd('.', '!', '?')
            if (item.length >= 3) {
                val a = memorias()
                a.put(item)
                while (a.length() > 30) a.remove(0)
                salvarMemorias(a)
                return "Anotado, senhor. Vou me lembrar de que $item."
            }
        }

        if (t.contains("o que voce lembra") || t.contains("o que voce anotou") || t.contains("minhas anotacoes") || t.contains("o que eu pedi para lembrar")) {
            val a = memorias()
            if (a.length() == 0) return "Não tenho nada anotado, senhor."
            var r = "Tenho anotado:"
            for (i in 0 until a.length()) r += " " + (i + 1) + ", " + a.getString(i) + "."
            return r
        }

        if (t.contains("esquec") && (t.contains("tudo") || t.contains("anotacoes") || t.contains("memorias"))) {
            salvarMemorias(JSONArray())
            return "Pronto, senhor. Apaguei minhas anotações."
        }

        val local = comandoLocal(t)
        if (local != null) return local
        return perguntar(original)
    }

    private fun comandoLocal(t: String): String? {
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
            ctx.startActivity(i)
        } catch (e: Exception) {
            i.setPackage(null)
            try {
                ctx.startActivity(i)
            } catch (e2: Exception) {
            }
        }
    }

    private fun abrirApp(nome: String): Boolean {
        val pm = ctx.packageManager
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
            ctx.startActivity(li)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun alarme(alvo: String): Boolean {
        val m = Regex("(\\d{1,2})\\s*[:h]\\s*(\\d{2})?").find(alvo) ?: return false
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, min)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun timer(alvo: String): Boolean {
        val seg = alvo.filter { it.isDigit() }.toIntOrNull() ?: return false
        if (seg <= 0) return false
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seg)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun lanterna(ligar: Boolean): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cm.setTorchMode(id, ligar)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun executar(acao: String, alvo: String): String? {
        when (acao) {
            "abrir_app" -> if (!abrirApp(norm(alvo).trim())) return "Não encontrei o aplicativo $alvo, senhor."
            "youtube" -> {
                if (alvo.isBlank()) {
                    if (!abrirApp("youtube")) return "Não consegui abrir o YouTube, senhor."
                } else {
                    abrirUrl("https://www.youtube.com/results?search_query=" + Uri.encode(alvo), "com.google.android.youtube")
                }
            }
            "pesquisar" -> abrirUrl("https://www.google.com/search?q=" + Uri.encode(alvo), null)
            "alarme" -> if (!alarme(alvo)) return "Não consegui programar o alarme, senhor."
            "timer" -> if (!timer(alvo)) return "Não consegui iniciar o timer, senhor."
            "lanterna_ligar" -> if (!lanterna(true)) return "Não consegui acender a lanterna, senhor."
            "lanterna_desligar" -> if (!lanterna(false)) return "Não consegui apagar a lanterna, senhor."
        }
        return null
    }

    private fun instrucaoAtual(): String {
        val f = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", Locale("pt", "BR"))
        var s = instrucoes + " Data e hora atuais: " + f.format(Date()) + "."
        val m = memorias()
        if (m.length() > 0) {
            s += " Coisas que o Tom pediu para você lembrar:"
            for (i in 0 until m.length()) s += " (" + (i + 1) + ") " + m.getString(i) + "."
        }
        return s
    }

    private fun perguntar(texto: String): String {
        if (chave().isEmpty()) return "Preciso da chave do Gemini, senhor. Abra o aplicativo e cole a chave."
        val turnoUser = JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", texto)))
        val conteudo = JSONArray()
        for (i in 0 until historico.length()) conteudo.put(historico.get(i))
        conteudo.put(turnoUser)
        val corpo = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instrucaoAtual()))))
            .put("contents", conteudo)
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=" + chave()
        var erro = ""
        for (tent in 0 until 3) {
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
                    val bruto = JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                        .getString("text").trim()
                    val limpo = bruto.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    var acao = "conversar"
                    var alvo = ""
                    var fala = limpo
                    try {
                        val j = JSONObject(limpo)
                        acao = j.optString("acao", "conversar")
                        alvo = j.optString("alvo", "")
                        fala = j.optString("fala", "")
                    } catch (e: Exception) {
                    }
                    if (fala.isBlank()) fala = "Pois não, senhor."
                    historico.put(turnoUser)
                    historico.put(JSONObject().put("role", "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", limpo))))
                    while (historico.length() > 30) {
                        historico.remove(0)
                        historico.remove(0)
                    }
                    prefs().edit().putString("hist2", historico.toString()).apply()
                    val falha = executar(acao, alvo)
                    return falha ?: fala
                } else if (codigo == 429) {
                    return "O limite gratuito do Google foi atingido, senhor. Aguarde um minuto e tente novamente."
                } else if (codigo == 400 || codigo == 401 || codigo == 403 || codigo == 404) {
                    return "Há um problema com a chave ou o modelo, senhor (erro $codigo). Abra o aplicativo e segure o título para trocar a chave."
                } else {
                    erro = "erro $codigo"
                    Thread.sleep(2000L * (tent + 1))
                }
            } catch (e: Exception) {
                erro = e.message ?: "falha"
                Thread.sleep(2000L * (tent + 1))
            }
        }
        return "Não consegui falar com o servidor, senhor ($erro). Tente de novo em instantes."
    }
}
