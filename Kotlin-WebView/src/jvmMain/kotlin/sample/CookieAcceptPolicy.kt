package sample

import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

class CookieAcceptPolicy : CookiePolicy {
    override fun shouldAccept(uri: URI?, cookie: HttpCookie?): Boolean {
        if (uri == null || cookie == null) {
            return false
        }

        // default policy does not accept host wwwrun.d3.mail.com as a sub-domain of ".mail.com"
        // see doc for HttpCookie.domainMatches
        //var domain = cookie.domain // i.e. ".mail.com"
        //var host = uri.host        // i.e. "wwwrun.d3.mail.com"

        return shouldAcceptHost(cookie.domain, uri.host)
    }

    fun shouldAcceptHost(domain: String, host: String): Boolean
    {
        if (!HttpCookie.domainMatches(domain, host)) {
            val domainNoDot = domain.removeSuffix(".")
            val hostNoDot = host.removeSuffix(".")

            return hostNoDot.endsWith(domainNoDot, true)//  host.substring(host.length - domain.length) == domain
        }

        return true

    }
}