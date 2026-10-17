package com.thomaz.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class Palavra(private val ctx: Context) {

    private val ui = Handler(Looper.getMainLooper())
    private var modelo: Model? = null
    private var servico: SpeechService? = null
    private var reconhecedor: Recognizer? = null
    private var preparando = false
    private var disparou = false
    private var ultimoOuvido = ""

    private val nomePasta = "vosk-model-small-pt-0.3"
    private val url = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
    private val regex = Regex("j[aá]r?[vb]|g[aá]r?[vb]|h[aá]rv|j[aá] v[ií]s|garbi|jarbi")

    fun preparar(status: (String) -> Unit, pronto: () -> Unit, erro: (String) -> Unit) {
        if (modelo != null) {
            pronto()
            return
        }
        if (preparando) return
        preparando = true
        thread {
            try {
                val base = File(ctx.filesDir, "vosk")
                val pasta = File(base, nomePasta)
                if (!File(pasta, "conf").exists()) {
                    base.mkdirs()
                    ui.post { status("Baixando modelo de voz (uns 30 MB)...") }
                    val zip = File(ctx.cacheDir, "modelo.zip")
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 15000
                    c.readTimeout = 30000
                    val total = c.contentLength
                    var lido = 0L
                    var ultimo = 0
                    c.inputStream.use { entrada ->
                        FileOutputStream(zip).use { saida ->
                            val buf = ByteArray(16384)
                            while (true) {
                                val n = entrada.read(buf)
                                if (n < 0) break
                                saida.write(buf, 0, n)
                                lido += n
                                if (total > 0) {
                                    val p = (lido * 100 / total).toInt()
                                    if (p >= ultimo + 10) {
                                        ultimo = p
                                        ui.post { status("Baixando modelo de voz... $p%") }
                                    }
                                }
                            }
                        }
                    }
                    ui.post { status("Instalando modelo de voz...") }
                    val raiz = base.canonicalPath
                    ZipInputStream(zip.inputStream()).use { z ->
                        while (true) {
                            val e = z.nextEntry ?: break
                            val alvo = File(base, e.name)
                            if (!alvo.canonicalPath.startsWith(raiz)) continue
                            if (e.isDirectory) {
                                alvo.mkdirs()
                            } else {
                                alvo.parentFile?.mkdirs()
                                FileOutputStream(alvo).use { o -> z.copyTo(o) }
                            }
                        }
                    }
                    zip.delete()
                }
                ui.post { status("Carregando modelo de voz...") }
                val m = Model(pasta.absolutePath)
                ui.post {
                    modelo = m
                    preparando = false
                    pronto()
                }
            } catch (e: Throwable) {
                ui.post {
                    preparando = false
                    erro("Falha no modelo de voz: " + (e.message ?: "erro") + ". Confira a internet.")
                }
            }
        }
    }

    fun iniciar(aoOuvir: () -> Unit, erro: (String) -> Unit) {
        val m = modelo ?: return
        parar()
        disparou = false
        ultimoOuvido = ""
        try {
            val r = Recognizer(m, 16000.0f)
            reconhecedor = r
            val s = SpeechService(r, 16000.0f)
            servico = s
            s.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    checar(hypothesis, "partial", aoOuvir)
                }
                override fun onResult(hypothesis: String?) {
                    checar(hypothesis, "text", aoOuvir)
                }
                override fun onFinalResult(hypothesis: String?) {}
                override fun onError(exception: Exception?) {
                    erro("Erro no microfone: " + (exception?.message ?: "erro"))
                }
                override fun onTimeout() {}
            })
        } catch (e: Throwable) {
            erro("Não consegui iniciar o microfone: " + (e.message ?: "erro"))
        }
    }

    private fun checar(json: String?, campo: String, aoOuvir: () -> Unit) {
        if (disparou || json == null) return
        val txt = try {
            JSONObject(json).optString(campo, "")
        } catch (e: Exception) {
            ""
        }
        if (txt.isNotEmpty() && txt != ultimoOuvido) {
            ultimoOuvido = txt
            ui.post {
                if (!disparou) {
                    val s = "Ouvi: " + txt
                    JarvisService.ultimoStatus = s
                    JarvisService.aoMudar?.invoke(s, 0)
                }
            }
        }
        if (txt.isNotEmpty() && regex.containsMatchIn(txt.lowercase())) {
            disparou = true
            ui.post {
                parar()
                aoOuvir()
            }
        }
    }

    fun parar() {
        val s = servico
        val r = reconhecedor
        servico = null
        reconhecedor = null
        try {
            s?.cancel()
        } catch (e: Throwable) {
        }
        try {
            r?.close()
        } catch (e: Throwable) {
        }
    }

    fun encerrar() {
        parar()
        try {
            modelo?.close()
        } catch (e: Throwable) {
        }
        modelo = null
    }
}
