package sample

import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpCookie
import java.net.URI

class Cookies {
    data class Cookie(
        @JvmField var name: String? = "",
        @JvmField var value: String? = "",
        @JvmField var json: String? = ""
    )

    @JvmField var cookies : MutableList<Cookie>? = mutableListOf()
}

private val cookies = Cookies()

object CookieStorage {
    fun getKnownCookies(): List<HttpCookie> {
        val cookieData = cookies.cookies!!

        return cookieData.map {
            val serializable = SerializableCookie.fromData(
                it.name!!,
                it.value!!,
                it.json!!
            )

            return@map serializable.getCookie()
        }
    }

    fun updateCookieList()
    {
        val knownCookies = cookies.cookies!!
        knownCookies.clear()

        val manager = CookieHandler.getDefault() as CookieManager

        knownCookies.clear()

        manager.cookieStore.cookies.forEach {
            val serializable = SerializableCookie(it)
            knownCookies.add(
                Cookies.Cookie(
                    serializable.getName(),
                    serializable.getValue(),
                    serializable.getJsonData()
                )
            )
        }

        System.out.println("Saved %d cookies".format(knownCookies.size))
    }

    fun getCookieString(host: String): String {
        val cookies = getKnownCookies()
        val policy = CookieAcceptPolicy()

        val builder = StringBuilder()

        cookies.forEach {
            if (policy.shouldAcceptHost(it.domain, host)) {
                if (builder.isNotEmpty()) {
                    builder.append("; ") // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cookie
                }

                builder.append(it.name)
                builder.append("=")
                builder.append(it.value)
            }
        }

        return builder.toString()
    }

    fun getCookieList(uri: URI): List<HttpCookie> {
        val cookies = getKnownCookies()
        val policy = CookieAcceptPolicy()

        val list = mutableListOf<HttpCookie>()

        cookies.forEach {
            if (policy.shouldAccept(uri, it)) {
                list.add(it)
            }
        }

        return list.toList()
    }
}