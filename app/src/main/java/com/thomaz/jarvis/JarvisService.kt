package com.thomaz.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.concurrent.thread

class JarvisService : Service() {

    companion object {
        const val ACAO_PARAR = "com.thomaz.jarvis.PARAR"
        const val ACAO_OUVIR = "com.thomaz.jarvis.OUVIR"
        var rodando = false
        var ultimoStatus = ""
        val linhas = ArrayList<String>()
        var aoMudar: ((String, Boolean) -> Unit)? = null
        var aoLogar: ((String) -> Unit)? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsPronto = false
    private var reconhecedor: SpeechRecognizer? = null
    private lateinit var cerebro: Cerebro
    private var ocupado = true
    private var parado = false
    private var comandoAte = 0L
    private val reiniciar = Runnable { iniciarEscuta() }
    private val aguardando = "Aguardando \"Jarvis\"..."

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cerebro = Cerebro(this)
        tts = TextToSpeech(this) { s ->
            ui.post {
                if (parado) return@post
                if (s == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("pt", "BR")
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            ui.post { fimDaFala() }
                        }
                        override fun onError(id: String?) {
                            ui.post { fimDaFala() }
                        }
                    })
                    ttsPronto = true
                    falar("Sistema online. Diga Jarvis quando precisar, senhor.")
                } else {
                    fimDaFala()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) {
            encerrar()
            return START_NOT_STICKY
        }
        val n = montarNotificacao()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
        rodando = true
        if (intent?.action == ACAO_OUVIR && !ocupado) {
            comandoAte = SystemClock.elapsedRealtime() + 8000
            ocupado = true
            ui.removeCallbacks(reiniciar)
            try {
                reconhecedor?.destroy()
            } catch (e: Exception) {
            }
            reconhecedor = null
            falar("Pois não, senhor?")
        }
        return START_NOT_STICKY
    }

    private fun montarNotificacao(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val parar = PendingIntent.getService(this, 1, Intent(this, JarvisService::class.java).setAction(ACAO_PARAR), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "jarvis")
            .setContentTitle("J.A.R.V.I.S. ativo")
            .setContentText("Diga \"Jarvis\" para chamar")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(abrir)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Desativar", parar).build())
            .setOngoing(true)
            .build()
    }

    private fun emComando(): Boolean = SystemClock.elapsedRealtime() < comandoAte

    private fun mudar(s: String, p: Boolean) {
        ultimoStatus = s
        aoMudar?.invoke(s, p)
    }

    private fun logar(l: String) {
        linhas.add(l)
        while (linhas.size > 40) linhas.removeAt(0)
        aoLogar?.invoke(l)
    }

    private fun agendar(ms: Long) {
        ui.removeCallbacks(reiniciar)
        ui.postDelayed(reiniciar, ms)
    }

    private fun iniciarEscuta() {
        if (parado || ocupado) return
        try {
            reconhecedor?.destroy()
        } catch (e: Exception) {
        }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        reconhecedor = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (emComando()) mudar("Ouvindo...", true) else mudar(aguardando, false)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    encerrar()
                    return
                }
                agendar(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 400L)
            }
            override fun onResults(results: Bundle?) {
                val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (texto.isNullOrBlank()) agendar(300L) else tratar(texto)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        r.startListening(i)
    }

    private fun tratar(texto: String) {
        val m = Regex("\\b(j[aáà]rvis|jarvi|garvis|j[aá]rves|harvis|jarvas)\\b", RegexOption.IGNORE_CASE).find(texto)
        if (m != null) {
            val resto = texto.substring(m.range.last + 1).trim(' ', ',', '.', '!', '?')
            if (resto.length >= 3) {
                comandoAte = 0L
                processar(resto)
            } else {
                comandoAte = SystemClock.elapsedRealtime() + 8000
                ocupado = true
                falar("Pois não, senhor?")
            }
        } else if (emComando()) {
            comandoAte = 0L
            processar(texto)
        } else {
            agendar(150L)
        }
    }

    private fun processar(cmd: String) {
        ocupado = true
        logar("Você: $cmd")
        mudar("Pensando...", true)
        thread {
            val fala = try {
                cerebro.processar(cmd)
            } catch (e: Exception) {
                "Ocorreu um erro, senhor."
            }
            ui.post {
                logar("Jarvis: $fala")
                falar(fala)
            }
        }
    }

    private fun falar(texto: String) {
        val limpo = texto.replace("*", "").replace("#", "").replace("_", " ")
        if (!ttsPronto || parado) {
            fimDaFala()
            return
        }
        mudar("Falando...", true)
        tts?.speak(limpo, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun fimDaFala() {
        if (parado) return
        ocupado = false
        mudar(aguardando, false)
        agendar(300L)
    }

    private fun encerrar() {
        parado = true
        rodando = false
        ui.removeCallbacks(reiniciar)
        try {
            reconhecedor?.destroy()
        } catch (e: Exception) {
        }
        reconhecedor = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        mudar("Jarvis desativado", false)
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        parado = true
        rodando = false
        ui.removeCallbacks(reiniciar)
        try {
            reconhecedor?.destroy()
        } catch (e: Exception) {
        }
        try {
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        super.onDestroy()
    }
}
