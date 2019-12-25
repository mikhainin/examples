package sample

import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.embed.swing.JFXPanel
import javafx.event.EventHandler
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebErrorEvent
import javafx.scene.web.WebView
import java.awt.EventQueue
import java.net.CookieHandler
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.swing.JFrame


class KotlinSwingSimpleEx(title: String) : JFrame() {

    init {
        createUI(title)
    }

    private fun createUI(title: String) {

        setTitle(title)

        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        setSize(300, 200)
        setLocationRelativeTo(null)

        val cookieManager = MyCookieManager()
        CookieHandler.setDefault(cookieManager);


        Platform.startup {
            var scene = Scene(Group())

            val webView = WebView()
            val webEngine = webView.engine

            val scrollPane = ScrollPane()
            scrollPane.setContent(webView)

            webEngine.isJavaScriptEnabled = true
            webEngine.loadWorker.stateProperty()
                .addListener { ov, oldState, newState ->
                    if (newState === javafx.concurrent.Worker.State.SUCCEEDED) {
                        setTitle(webEngine.location)
                        System.out.println(webEngine.location);
                        // webEngine.executeScript("if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}");
                        CookieStorage.updateCookieList()
                    }
                }

            webEngine.loadWorker.exceptionProperty()
                .addListener(ChangeListener<Throwable> { observable, oldValue, newValue -> System.out.println("Exception: " + newValue.message) })

            webEngine.setOnError(EventHandler<WebErrorEvent> { webErrorEvent -> System.out.println("On Error: " + webErrorEvent.message) })

            val trustAllCerts: Array<TrustManager> = arrayOf<TrustManager>(
                object : X509TrustManager {

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }

                    override fun checkClientTrusted(
                        certs: Array<X509Certificate?>?, authType: String?
                    ) {
                    }

                    override fun checkServerTrusted(
                        certs: Array<X509Certificate?>?, authType: String?
                    ) {
                    }
                }
            )

            // Install the all-trusting trust manager
            try {
                val sc: SSLContext = SSLContext.getInstance("SSL")
                sc.init(null, trustAllCerts, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            } catch (e: GeneralSecurityException) {
            }

            webEngine.load("https://www.google.com/")

            scene.root = scrollPane

            val borderPane = BorderPane()
            borderPane.center = webView

            scene = Scene(borderPane, 900.0, 700.0)
            val myFXPanel = JFXPanel()
            myFXPanel.setScene(scene)

            add(myFXPanel)
        }
    }
}

private fun createAndShowGUI() {

    val frame = KotlinSwingSimpleEx("Simple")
    frame.isVisible = true
}

fun main(args: Array<String>) {
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true")
    EventQueue.invokeLater(::createAndShowGUI)
}