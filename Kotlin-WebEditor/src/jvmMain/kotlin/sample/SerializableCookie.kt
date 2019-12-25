package sample

import org.apache.commons.lang.reflect.FieldUtils
import java.io.StringReader
import java.lang.Exception
import java.net.HttpCookie
import javax.json.Json

class SerializableCookie(private val cookie: HttpCookie)
{
    fun getName(): String = cookie.name

    fun getValue(): String = cookie.value

    fun getJsonData() : String
    {
        val whenCreated = try {
            val field = FieldUtils.getField(HttpCookie::class.java, "whenCreated", true)
            field?.getLong(cookie) ?: System.currentTimeMillis()
        } catch (e: NoClassDefFoundError) {
            System.out.println("Failed to get whenCreated: ${e.message}")
            System.currentTimeMillis()
        }

        val builder = Json.createObjectBuilder()
        if (cookie.domain != null) builder.add("domain", cookie.domain)
        if (cookie.comment != null) builder.add("comment", cookie.comment)
        if (cookie.commentURL != null) builder.add("commentURL", cookie.commentURL)
        if (cookie.path != null) builder.add("path", cookie.path)
        if (cookie.portlist != null) builder.add("portlist", cookie.portlist)

        return builder
            .add("discard", cookie.discard)
            .add("isHttpOnly", cookie.isHttpOnly)
            .add("maxAge", cookie.maxAge)
            .add("secure", cookie.secure)
            .add("version", cookie.version)
            .add("whenCreated", whenCreated)
            .build()
            .toString()
    }

    fun getCookie() = cookie

    companion object {
        fun fromData(name: String, value: String, jsonData : String): SerializableCookie {
            val cookie = HttpCookie(name, value)

            val jsonObject = Json.createReader(StringReader(jsonData)).readObject()

            if (jsonObject.containsKey("domain"))     cookie.domain     = jsonObject.getString("domain")
            if (jsonObject.containsKey("comment"))    cookie.comment    = jsonObject.getString("comment")
            if (jsonObject.containsKey("commentURL")) cookie.commentURL = jsonObject.getString("commentURL")
            if (jsonObject.containsKey("path"))       cookie.path       = jsonObject.getString("path")
            if (jsonObject.containsKey("portlist"))   cookie.portlist   = jsonObject.getString("portlist")

            cookie.discard = jsonObject.getBoolean("discard")
            cookie.isHttpOnly = jsonObject.getBoolean("isHttpOnly")
            cookie.maxAge = jsonObject.getInt("maxAge").toLong()
            cookie.secure = jsonObject.getBoolean("secure")
            cookie.version = jsonObject.getInt("version")

            try {
                val field = FieldUtils.getField(HttpCookie::class.java, "whenCreated", true)
                field?.setLong(cookie, jsonObject.getJsonNumber("whenCreated").longValue())
            } catch (e: Exception) {
                System.out.println("Failed to set whenCreated: ${e.message}")
            }

            return SerializableCookie(cookie)
        }
    }
}