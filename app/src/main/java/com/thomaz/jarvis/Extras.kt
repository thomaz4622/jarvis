package com.thomaz.jarvis

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import java.text.Normalizer
import java.util.Locale

object Extras {

    private fun norm(s: String): String {
        var t = Normalizer.normalize(s.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[,.!?]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        t = t.replace(Regex("^((por favor|ok|entao|pode|poderia|voce pode) )+"), "")
        t = t.replace(Regex(" por favor$"), "")
        return t.trim()
    }

    private fun tecla(am: AudioManager, codigo: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, codigo))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, codigo))
    }

    private fun mexerVolume(am: AudioManager, direcao: Int) {
        for (i in 0 until 3) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direcao, if (i == 2) AudioManager.FLAG_SHOW_UI else 0)
        }
    }

    private fun tocarMusica(ctx: Context, q: String) {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        i.putExtra(SearchManager.QUERY, q)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            val v = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
            v.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(v)
            } catch (e2: Exception) {
            }
        }
    }

    fun tentar(ctx: Context, original: String): String? {
        val t = norm(original)

        if (t.contains("noticia") || t.contains("manchete")) {
            return Noticias.resumoFalado()
        }

        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Regex("^(pausa|pausar|pause|pare|parar|para)( a| o)?( musica| som| video| reproducao| tudo)?$").containsMatchIn(t)) {
            tecla(am, KeyEvent.KEYCODE_MEDIA_PAUSE)
            return "Pausado, senhor."
        }
        if (Regex("^(continua|continuar|retoma|retomar|despausa|despausar|da play|dar play|play)( a| o)?( musica| som| video| reproducao)?$").containsMatchIn(t)) {
            tecla(am, KeyEvent.KEYCODE_MEDIA_PLAY)
            return "Retomando, senhor."
        }
        if (t.contains("proxima musica") || t.contains("proxima faixa") || t == "proxima" || t.contains("pula a musica") || t.contains("pular musica") || t.contains("passa a musica")) {
            tecla(am, KeyEvent.KEYCODE_MEDIA_NEXT)
            return "Próxima faixa, senhor."
        }
        if (t.contains("musica anterior") || t.contains("faixa anterior") || t == "anterior" || t.contains("volta a musica")) {
            tecla(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return "Faixa anterior, senhor."
        }

        if (t.contains("volume")) {
            val musica = AudioManager.STREAM_MUSIC
            if (t.contains("maximo")) {
                am.setStreamVolume(musica, am.getStreamMaxVolume(musica), AudioManager.FLAG_SHOW_UI)
                return "Volume no máximo, senhor."
            }
            if (t.contains("minimo")) {
                am.setStreamVolume(musica, 1, AudioManager.FLAG_SHOW_UI)
                return "Volume no mínimo, senhor."
            }
            if (t.contains("aument") || t.contains("sobe") || t.contains("subir") || t.contains("mais alto")) {
                mexerVolume(am, AudioManager.ADJUST_RAISE)
                return "Volume aumentado, senhor."
            }
            if (t.contains("diminu") || t.contains("abaix") || t.contains("baixa") || t.contains("descer") || t.contains("mais baixo")) {
                mexerVolume(am, AudioManager.ADJUST_LOWER)
                return "Volume reduzido, senhor."
            }
            if (t.contains("mudo") || t.contains("silenc")) {
                am.adjustStreamVolume(musica, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                return "Som silenciado, senhor."
            }
        }

        if (!t.contains("youtube")) {
            val m = Regex("^(?:toca|tocar|toque|reproduz|reproduzir) (?:a |o |as |os |uma |um )?(?:musica |musicas |cancao |som |playlist )?(?:de |do |da |dos |das )?(.+)$").find(t)
            if (m != null) {
                val q = m.groupValues[1].trim()
                if (q == "musica" || q == "musicas" || q == "som") {
                    tecla(am, KeyEvent.KEYCODE_MEDIA_PLAY)
                    return "Retomando, senhor."
                }
                if (q.isNotEmpty()) {
                    tocarMusica(ctx, q)
                    return "Tocando $q, senhor."
                }
            }
        }
        return null
    }
}
