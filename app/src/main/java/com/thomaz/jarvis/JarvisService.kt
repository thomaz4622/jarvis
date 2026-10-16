package com.thomaz.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.concurrent.thread

class JarvisService : Service() {

    companion object {
        const val ACAO_PARAR = "com.thomaz.jarvis.PARAR"
        const val ACAO_OUVIR = "com.thomaz.jarvis.OUVIR"
        const val M_ESPERA = 0
        const val M_OUVINDO = 1
        const val M_PENSANDO = 2
        const val M_FALANDO = 3
        var rodando = false
        var ultimoStatus = ""
        var ultimoModo = 0
        var totalLinhas = 0
        val linhas = ArrayList<String>()
        var aoMudar: ((String, Int) -> Unit)? = null
        var aoLogar: ((String) -> Unit)? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var voz: Voz
    private lateinit var cerebro: Cerebro
    private lateinit var palavra: Palavra
    private var reconhecedor: SpeechRecognizer? = null
    private var ocupado = true
    private var parado = false
    private val aguardando = "Aguardando \"Jarvis\"..."

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cerebro = Cerebro(this)
        voz = Voz(this)
        palavra = Palavra(this)
        voz.iniciar {
            if (!parado) falar("Sistema online. Diga Jarvis, espere o bip e fale o comando, senhor.")
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
            ocupado = true
            palavra.parar()
            bip()
            ui.postDelayed({ ouvirComando() }, 250)
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

    private fun mudar(s: String, m: Int) {
        ultimoStatus = s
        ultimoModo = m
        aoMudar?.invoke(s, m)
    }

    private fun logar(l: String) {
        linhas.add(l)
        totalLinhas++
        while (linhas.size > 40) linhas.removeAt(0)
        aoLogar?.invoke(l)
    }

    private fun bip() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            ui.postDelayed({ tg.release() }, 400)
        } catch (e: Exception) {
        }
    }

    private fun iniciarPalavra() {
        if (parado || ocupado) return
        palavra.preparar(
            { s -> if (!parado) mudar(s, M_ESPERA) },
            {
                if (!parado && !ocupado) {
                    palavra.iniciar(
                        { ui.post { acordar() } },
                        { msg -> ui.post { mudar(msg, M_ESPERA) } }
                    )
                    mudar(aguardando, M_ESPERA)
                }
            },
            { msg -> ui.post { mudar(msg, M_ESPERA) } }
        )
    }

    private fun acordar() {
        if (parado || ocupado) return
        ocupado = true
        palavra.parar()
        mudar("Ouvindo...", M_OUVINDO)
        bip()
        ui.postDelayed({ ouvirComando() }, 300)
    }

    private fun ouvirComando() {
        if (parado) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voltar()
            return
        }
        mudar("Ouvindo...", M_OUVINDO)
        try {
            reconhecedor?.destroy()
        } catch (e: Exception) {
        }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        reconhecedor = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    encerrar()
                    return
                }
                voltar()
            }
            override fun onResults(results: Bundle?) {
                val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (texto.isNullOrBlank()) voltar() else processar(texto)
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

    private fun processar(cmd: String) {
        ocupado = true
        logar("Você: $cmd")
        mudar("Pensando...", M_PENSANDO)
        thread {
            val fala = try {
                Extras.tentar(this, cmd) ?: cerebro.processar(cmd)
            } catch (e: Exception) {
                "Ocorreu um erro, senhor."
            }
            ui.post {
                if (parado) return@post
                logar("Jarvis: $fala")
                falar(fala)
            }
        }
    }

    private fun falar(texto: String) {
        mudar("Falando...", M_FALANDO)
        voz.falar(texto) { voltar() }
    }

    private fun voltar() {
        ocupado = false
        if (parado) return
        mudar(aguardando, M_ESPERA)
        ui.postDelayed({ iniciarPalavra() }, 400)
    }

    private fun encerrar() {
        parado = true
        rodando = false
        try {
            reconhecedor?.destroy()
        } catch (e: Exception) {
        }
        reconhecedor = null
        palavra.encerrar()
        voz.encerrar()
        mudar("Jarvis desativado", M_ESPERA)
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        parado = true
        rodando = false
        try {
            reconhecedor?.destroy()
        } catch (e: Exception) {
        }
        try {
            palavra.encerrar()
        } catch (e: Exception) {
        }
        try {
            voz.encerrar()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
