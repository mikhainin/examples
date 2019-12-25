package sample

import java.io.IOException
import java.net.CookieManager
import java.net.HttpCookie
import java.net.URI
import java.util.*


class MyCookieManager internal constructor() : CookieManager(null, CookieAcceptPolicy()) {
    private val acceptPolicy = CookieAcceptPolicy()

    init {
        cookieStore.removeAll()

        CookieStorage.getKnownCookies().forEach {
            cookieStore.add(null, it)
        }
    }

    @Throws(IOException::class)
    override fun get(uri: URI, requestHeaders: Map<String, List<String>>): Map<String, List<String>> {
        println("----------- cookie GET -------------")
        println("URI: $uri")

        val result = getCookies(uri)

        result.forEach { (uri, cookies) ->
            println("    for '$uri'")
            cookies.forEach {
                println("        for '$it'")
            }
        }

        return result
    }

    @Throws(IOException::class)
    override fun put(uri: URI, responseHeaders: Map<String, List<String>>) {
        println("----------- cookie PUT -------------")
        println("URI: $uri")
        responseHeaders.forEach { (uri, cookies) ->
            println("    for '$uri'")
            cookies.forEach {
                println("        -> '$it'")
            }
        }

        super.put(uri, responseHeaders)
    }

    private fun getCookies(uri: URI): Map<String, List<String>> {
        val isSecure = uri.scheme == "https"
                    || uri.scheme == "wss"
                    || uri.scheme == "javascripts" // have no idea what it is but without this scheme Google authentication doesn't work

        val matchedCookies = cookieStore.cookies.filter {
            acceptPolicy.shouldAccept(uri, it)
                    && (!it.secure || isSecure)
        }

        return mapOf(Pair("Cookie", sortByPath(matchedCookies)))
    }

    private fun sortByPath(cookies: List<HttpCookie>): List<String> {
        Collections.sort(cookies, CookiePathComparator())
        val cookieHeader = arrayListOf<String>()
        var cookie: HttpCookie
        val var3: Iterator<*> = cookies.iterator()
        while (var3.hasNext()) {
            cookie = var3.next() as HttpCookie
            if (cookies.indexOf(cookie) == 0 && cookie.version > 0) {
                cookieHeader.add("\$Version=\"1\"")
            }
            cookieHeader.add(cookie.toString())
        }

        return cookieHeader
    }

    internal class CookiePathComparator : Comparator<HttpCookie?> {
        override fun compare(c1: HttpCookie?, c2: HttpCookie?): Int {
            return if (c1 === c2) {
                0
            } else if (c1 == null) {
                -1
            } else if (c2 == null) {
                1
            } else if (c1.name != c2.name) {
                0
            } else if (c1.path.startsWith(c2.path)) {
                -1
            } else {
                if (c2.path.startsWith(c1.path)) 1 else 0
            }
        }
    }
}