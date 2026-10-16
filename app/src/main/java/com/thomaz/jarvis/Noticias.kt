package com.thomaz.jarvis

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

object Noticias {

    class Item(val titulo: String, val link: String, val fonte: String)

    private val feeds = listOf(
        Pair("G1", "https://g1.globo.com/rss/g1/"),
        Pair("G1 Mundo", "https://g1.globo.com/rss/g1/mundo/"),
        Pair("G1 Economia", "https://g1.globo.com/rss/g1/economia/"),
        Pair("G1 Tecnologia", "https://g1.globo.com/dynamo/tecnologia/rss2.xml"),
        Pair("BBC Brasil", "https://feeds.bbci.co.uk/portuguese/rss.xml")
    )

    fun buscar(): List<Item> {
        val listas = ArrayList<List<Item>>()
        val threads = ArrayList<Thread>()
        for (f in feeds) {
            val t = Thread {
                try {
                    val l = ler(f.first, f.second)
                    synchronized(listas) { listas.add(l) }
                } catch (e: Exception) {
                }
            }
            threads.add(t)
            t.start()
        }
        for (t in threads) t.join()

        val saida = ArrayList<Item>()
        val vistos = HashSet<String>()
        var i = 0
        while (saida.size < 40) {
            var achou = false
            for (l in listas) {
                if (i < l.size) {
                    achou = true
                    if (vistos.add(l[i].titulo)) saida.add(l[i])
                }
            }
            if (!achou) break
            i++
        }
        return saida
    }

    private fun ler(fonte: String, url: String): List<Item> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Jarvis")
        val itens = ArrayList<Item>()
        c.inputStream.use { entrada ->
            val p = Xml.newPullParser()
            p.setInput(entrada, null)
            var ev = p.eventType
            var dentro = false
            var tag = ""
            var titulo = ""
            var link = ""
            while (ev != XmlPullParser.END_DOCUMENT && itens.size < 15) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = p.name
                    if (tag == "item") {
                        dentro = true
                        titulo = ""
                        link = ""
                    }
                } else if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) {
                    if (dentro) {
                        if (tag == "title") {
                            titulo += p.text
                        } else if (tag == "link") {
                            link += p.text
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (p.name == "item") {
                        dentro = false
                        if (titulo.isNotBlank() && link.isNotBlank()) {
                            itens.add(Item(limpar(titulo), link.trim(), fonte))
                        }
                    }
                    tag = ""
                }
                ev = p.next()
            }
        }
        return itens
    }

    private fun limpar(s: String): String =
        s.trim().replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")

    fun resumoFalado(): String {
        val l = buscar()
        if (l.isEmpty()) return "Não consegui acessar as notícias agora, senhor."
        var r = "Estas são as principais manchetes, senhor."
        var n = 0
        for (item in l) {
            if (n >= 4) break
            r += " " + (n + 1) + ". " + item.titulo + "."
            n++
        }
        return r
    }
}
