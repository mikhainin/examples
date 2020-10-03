import UnixSocket.CLib.Companion.c
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
//import /jnr.constants.platform.Errno
//import jnr.ffi.LastError
//import jnr.ffi.Runtime
import org.apache.commons.lang3.SystemUtils
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class UnixSocket {
    private val sock: Int
    private val PF_UNIX = 1
    private val SOCK_STREAM = 1

    private val MSG_DONTWAIT = 0x80

    private val EAGAIN = if (SystemUtils.IS_OS_MAC) 35 else 11 // Mac OS value 35, it used to be 11. Linux has 11
    private val EWOULDBLOCK = EAGAIN // Posix allows these constants to have different values but in fact they're the same on Linux and Mac. So far.

    private var isConnected = AtomicBoolean(true)

    private interface CLib : Library {
        fun socket(domain: Int, type: Int, protocol: Int): Int
        fun connect(sockfd: Int, addr: Pointer?, addrlen: Int): Int
        fun close(fd: Int): Int
        fun write(fd: Int, buf: ByteArray?, count: Int): Int
        fun recv(fd: Int, buf: ByteArray?, len: Int, flags: Int): Int

        companion object {
            val c = Native.load("c", CLib::class.java) as CLib
        }
    }
    class SockAddress : Structure() {
        @JvmField var sun_family: Short = 0
        @JvmField var sun_path: ByteArray = ByteArray(0)

        override fun getFieldOrder(): MutableList<String> {
            return mutableListOf("sun_family", "sun_path")
        }
    }

    constructor(socketPath: String) {
        sock = c.socket(
            PF_UNIX,  // AF_UNIX
            SOCK_STREAM,  // SOCK_STREAM
            0
        )
        if (sock < 0) {
            throw IOException("failed to allocate usocket")
        }

        val sockaddr = SockAddress()
        sockaddr.sun_family = 1
        sockaddr.sun_path = ByteArray(108)
        System.arraycopy(
            socketPath.toByteArray(), 0,
            sockaddr.sun_path, 0,
            socketPath.length
        )
        sockaddr.write()

        val foo = c.connect(sock, sockaddr.pointer, sockaddr.size())
        if (foo < 0) {
            throw IOException("failed to fctrl usocket: $foo")
        }
    }

    fun close() {
        if (isConnected.getAndSet(false)) {
            c.close(sock)
        }
    }

    fun isConnected() = isConnected

    @Throws(IOException::class)
    fun write(buf: ByteArray, pos: Int, len: Int) {
        if (!isConnected.get()) {
            throw IOException("Socket is closed")
        }
        var _buf = buf
        if (pos != 0) {
            _buf = ByteArray(len)
            System.arraycopy(buf, pos, _buf, 0, len)
        }
        val result = c.write(sock, _buf, len)
        // TODO: check if all bytes are written
        if (result == -1) {
            close()
            throw IOException("Socket $sock write failed: ${Native.getLastError()}")
        }
    }

    @Throws(IOException::class)
    fun read(): ByteArray {
        val result = ByteArray(1024)

        val readBytes = c.recv(sock, result, 1024, MSG_DONTWAIT)
        if (readBytes == -1) {
            val lastError = Native.getLastError() // getLastError()
            if (lastError == EAGAIN || lastError == EWOULDBLOCK) {
                return ByteArray(0)
            }

            throw IOException("Socket $sock read failed: $lastError")
        }

        return result
    }

    @Throws(IOException::class)
    fun read(result: ByteArray): Int {
        val bytesRead = c.recv(sock, result, 1024, MSG_DONTWAIT)

        if (bytesRead == -1) {
            val lastError = Native.getLastError()
            if (lastError == EAGAIN || lastError == EWOULDBLOCK) {
                return 0
            }

            throw IOException("Socket $sock read failed: $lastError")
        }

        return bytesRead
    }
}