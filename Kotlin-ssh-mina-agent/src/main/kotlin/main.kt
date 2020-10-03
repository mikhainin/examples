//import com.jcraft.jsch.agentproxy.AgentProxy
//import com.jcraft.jsch.agentproxy.Identity
//import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector
//import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory
import org.apache.sshd.agent.SshAgent
import org.apache.sshd.agent.local.AgentImpl
import org.apache.sshd.agent.local.LocalAgentFactory
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.signature.BuiltinSignatures
import org.apache.sshd.common.signature.Signature
import org.apache.sshd.common.util.GenericUtils
import org.apache.sshd.common.util.ValidateUtils
import org.apache.sshd.common.util.buffer.keys.BufferPublicKeyParser
import org.apache.sshd.common.util.security.SecurityUtils
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.security.KeyPair
import java.security.PublicKey
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.util.*
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.concurrent.TimeUnit

fun main() {
    val root = LoggerFactory.getLogger(ROOT_LOGGER_NAME)
    root.debug("debug")
    root.info("info")
    root.error("error")

    val username = "negram"
    val host = "example.zone"
    val keyName = "ssh-rsa"

    val client = SshClient.setUpDefaultClient()
    client.serverKeyVerifier = AcceptAllServerKeyVerifier.INSTANCE
    client.hostConfigEntryResolver = HostConfigEntryResolver.EMPTY
    client.keyIdentityProvider = KeyIdentityProvider.EMPTY_KEYS_PROVIDER

//    val agentFactory = LocalAgentFactory()
//    val keys = FileKeyPairProvider(DefaultClientIdentitiesWatcher.getDefaultBuiltinIdentitiesPaths())

//    val minaAgent = AgentImpl()
//    val ap = mkAgentProxy()
//    ap.identities.forEach {
//        print(it.comment)
//        it.blob
//    }

//    val agent = mkAgentMina(ap)
//    val agentFactory = LocalAgentFactory(agent)
//    client.agentFactory = agentFactory

    // keys.passwordFinder = makeKeyLoader(host, username, false)

    //// SSH Agent test
    val myAgent = AgentClient(null, System.getenv("SSH_AUTH_SOCK"))
//    myAgent.isOpen
//    myAgent.identities.forEach {
//        root.info("id: {} -> {}", it.key, it.component2())
//    }

    client.start()
    val session = client.connect(username, host, 22)
        .verify(100000)
        .session

    client.agentFactory = LocalAgentFactory(myAgent)

    // val identity = keys.loadKey(session, keyName)
        // ?: throw Exception("Can't get credentials for $host / $keyName")
    // agentFactory.agent.addIdentity(identity, username)

    // session.addPublicKeyIdentity(identity)

    session.auth().verify(500L, TimeUnit.SECONDS)

    println("Connected")

    val result = session.executeRemoteCommand("hostname -f")

    println("from remote server: $result")

    session.close(true)
    client.stop()
}
/*

fun mkAgentProxy(): AgentProxy {
    val udsf = JNAUSocketFactory()
    val ap = AgentProxy(SSHAgentConnector(udsf))

    return ap
}

fun mkAgentMina(ap: AgentProxy) : SshAgent {
    return object : SshAgent {
        private val identity = ap.identities.first()
        private val keys = arrayListOf<SimpleImmutableEntry<PublicKey, String>>()
        private val identities = arrayListOf<Identity>()

        override fun close() {
            // ap.connector.name
            TODO("Not yet implemented")
        }

        override fun isOpen(): Boolean {
            return ap.isRunning
        }

        override fun getIdentities(): MutableIterable<MutableMap.MutableEntry<PublicKey, String>> {
            keys.clear()

            ap.identities.forEach {
                val ba = org.apache.sshd.common.util.buffer.ByteArrayBuffer(it.blob)
                val key = ba.getRawPublicKey(BufferPublicKeyParser.DEFAULT)
                val comment = it.comment.toString(Charset.defaultCharset())
                keys.add(SimpleImmutableEntry<PublicKey, String>(key, comment))

                identities.add(it)
                // keys.add(SimpleImmutableEntry<PublicKey, String>(key, comment))
            }

            return keys
        }

        override fun sign(key: PublicKey, data: ByteArray?): ByteArray? {
            val root = LoggerFactory.getLogger(javaClass.name)
            for (i in 0 until keys.size) {
                if (keys[i].key == key) {
                    root.info("sign: key: {}, data: {}", key.toString(), data.toString())

                    return ap.sign(identities[i].blob, data)
                }
            }

            root.error("key not found: ", key.toString())

            return byteArrayOf()
        }

        protected fun getKeyPair(
            keys: MutableList<SimpleImmutableEntry<PublicKey, String>>, key: PublicKey?
        ): SimpleImmutableEntry<PublicKey, String>? {
            if (GenericUtils.isEmpty(keys) || key == null) {
                return null
            }
            for (k in keys) {
                val kp = k!!.key
                if (KeyUtils.compareKeys(key, kp)) {
                    return k
                }
            }
            return null
        }

        override fun addIdentity(key: KeyPair?, comment: String?) {
            TODO("Not yet implemented")
        }

        override fun removeIdentity(key: PublicKey?) {
            TODO("Not yet implemented")
        }

        override fun removeAllIdentities() {
            TODO("Not yet implemented")
        }
    }
}*/
