package com.thomaz.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class Voz(private val ctx: Context) {

    private val ui = Handler(Looper.getMainLooper())
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var ttsPronto = false
    private var aoTerminarLocal: (() -> Unit)? = null
    @Volatile
    private var geracao = 0
    private var track: AudioTrack? = null
    private var foco: AudioFocusRequest? = null

    private val atributos = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val modelo = "gemini-2.5-flash-preview-tts"
    private val vozGemini = "Charon"
    private val estilo = "Fale em português do Brasil, com voz grave, calma, elegante e sofisticada, como um mordomo britânico muito inteligente e levemente irônico: "

    fun iniciar(aoPronto: () -> Unit) {
        tts = TextToSpeech(ctx) { s ->
            ui.post {
                if (s == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("pt", "BR")
                    tts?.setPitch(0.75f)
                    tts?.setSpeechRate(0.95f)
                    tts?.setAudioAttributes(atributos)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            ui.post { if (id == "j" + geracao) terminouLocal() }
                        }
                        override fun onError(id: String?) {
                            ui.post { if (id == "j" + geracao) terminouLocal() }
                        }
                    })
                    ttsPronto = true
                }
                aoPronto()
            }
        }
    }

    private fun terminouLocal() {
        val f = aoTerminarLocal
        aoTerminarLocal = null
        liberarFoco()
        f?.invoke()
    }

    private fun pegarFoco() {
        try {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(atributos)
                .setOnAudioFocusChangeListener { }
                .build()
            foco = r
            am.requestAudioFocus(r)
        } catch (e: Exception) {
        }
    }

    private fun liberarFoco() {
        try {
            val r = foco
            if (r != null) am.abandonAudioFocusRequest(r)
        } catch (e: Exception) {
        }
        foco = null
    }

    private fun parar0() {
        try {
            track?.stop()
        } catch (e: Exception) {
        }
        try {
            track?.release()
        } catch (e: Exception) {
        }
        track = null
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
        aoTerminarLocal = null
        liberarFoco()
    }

    fun parar() {
        geracao++
        parar0()
    }

    fun encerrar() {
        geracao++
        parar0()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
    }

    fun falar(texto: String, aoTerminar: () -> Unit) {
        val minha = ++geracao
        parar0()
        val limpo = texto.replace("*", "").replace("#", "").replace("_", " ").trim()
        val p = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val chave = p.getString("chave", "") ?: ""
        val usarGemini = chave.isNotEmpty() && limpo.length <= 700 && p.getBoolean("voz_gemini", true)
        if (!usarGemini) {
            falarLocal(limpo, minha, aoTerminar)
            return
        }
        thread {
            val pcm = baixarAudio(limpo, chave)
            ui.post {
                if (minha != geracao) return@post
                if (pcm == null) falarLocal(limpo, minha, aoTerminar) else tocarPcm(pcm, minha, aoTerminar)
            }
        }
    }

    private fun baixarAudio(texto: String, chave: String): ByteArray? {
        return try {
            val corpo = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", estilo + texto)))))
                .put("generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", vozGemini)))))
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelo + ":generateContent?key=" + chave).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.connectTimeout = 10000
            c.readTimeout = 30000
            c.doOutput = true
            c.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }
            if (c.responseCode != 200) return null
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val dados = JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getJSONObject("inlineData").getString("data")
            Base64.decode(dados, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    private fun tocarPcm(pcm: ByteArray, minha: Int, aoTerminar: () -> Unit) {
        pegarFoco()
        thread {
            var t: AudioTrack? = null
            try {
                val formato = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val a = AudioTrack.Builder()
                    .setAudioAttributes(atributos)
                    .setAudioFormat(formato)
                    .setBufferSizeInBytes(pcm.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                t = a
                track = a
                a.write(pcm, 0, pcm.size)
                a.play()
                val quadros = pcm.size / 2
                var espera = 0
                while (minha == geracao && a.playbackHeadPosition < quadros && espera < 1200) {
                    Thread.sleep(50)
                    espera++
                }
            } catch (e: Exception) {
            }
            try {
                t?.stop()
            } catch (e: Exception) {
            }
            try {
                t?.release()
            } catch (e: Exception) {
            }
            val fim = t
            ui.post {
                if (track === fim) track = null
                if (minha == geracao) {
                    liberarFoco()
                    aoTerminar()
                }
            }
        }
    }

    private fun falarLocal(texto: String, minha: Int, aoTerminar: () -> Unit) {
        if (!ttsPronto) {
            aoTerminar()
            return
        }
        pegarFoco()
        aoTerminarLocal = aoTerminar
        val r = tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "j" + minha)
        if (r != TextToSpeech.SUCCESS) terminouLocal()
    }
}
