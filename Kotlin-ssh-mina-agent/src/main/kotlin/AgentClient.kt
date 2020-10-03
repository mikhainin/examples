/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

//import jnr.unixsocket.UnixSocketAddress
//import jnr.unixsocket.UnixSocketChannel
import org.apache.sshd.agent.common.AbstractAgentProxy
import org.apache.sshd.common.FactoryManager
import org.apache.sshd.common.FactoryManagerHolder
import org.apache.sshd.common.PropertyResolverUtils
import org.apache.sshd.common.SshException
import org.apache.sshd.common.util.buffer.Buffer
import org.apache.sshd.common.util.buffer.ByteArrayBuffer
import org.apache.sshd.common.util.threads.CloseableExecutorService
import org.apache.sshd.common.util.threads.ThreadUtils
import java.io.*
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


/**
 * A client for a remote SSH agent
 */
class AgentClient @JvmOverloads constructor(
    private val manager: FactoryManager?,
    private val authSocket: String,
    executor: CloseableExecutorService? = null
) :
    AbstractAgentProxy(executor ?: ThreadUtils.newSingleThreadExecutor("AgentClient[$authSocket]")),
    Runnable, FactoryManagerHolder {
    // private val manager: FactoryManager = Objects.requireNonNull(manager, "No factory manager instance provided")!!

    // private val unixSocketChannel : UnixSocketChannel
    // private val unixSocketChannel : USocketFactory.Socket
    private val unixSocketChannel : UnixSocket
    private var receiveBuffer: Buffer = ByteArrayBuffer()
    private var messages = ArrayBlockingQueue<Buffer>(10)
    private val lock = java.lang.Object()

    private var pumper: Future<*>? = null
    private val open = AtomicBoolean(true)
    override fun getFactoryManager(): FactoryManager? {
        return manager
    }

    override fun isOpen(): Boolean {
        return open.get()
    }

    override fun run() {
        try {
            while (isOpen) {
                val buffer = ByteArray(1024)
                val bytesRead = unixSocketChannel.read(buffer)
                // val buffer = unixSocketChannel.read()
                if (bytesRead > 0) {
                    messageReceived(ByteArrayBuffer(buffer, 0, bytesRead))
                } else {
                    Thread.sleep(200)
                }
                /*
                Channels.newInputStream(unixSocketChannel).use { reader ->
                    val available = reader.available()
                    if (reader.available() > 0) {
                        val buffer = reader.readNBytes(available)
                        messageReceived(ByteArrayBuffer(buffer, 0, buffer.size))
                    } else {
                        Thread.sleep(200)
                    }
                }*/
            }
        } catch (e: Exception) {
            val debugEnabled = log.isDebugEnabled
            if (isOpen) {
                log.warn(
                    "run({}) {} while still open: {}",
                    this, e.javaClass.simpleName, e.message
                )
                if (debugEnabled) {
                    log.debug("run($this) open client exception", e)
                }
            } else {
                if (debugEnabled) {
                    log.debug("run($this) closed client loop exception", e)
                }
            }
        } finally {
            try {
                close()
            } catch (e: IOException) {
                if (log.isDebugEnabled) {
                    log.debug(
                        "run({}) {} while closing: {}",
                        this, e.javaClass.simpleName, e.message
                    )
                }
            }
        }
    }

    @Throws(Exception::class)
    protected fun messageReceived(buffer: Buffer?) {
        var message: Buffer? = null
        synchronized(receiveBuffer!!) {
            receiveBuffer!!.putBuffer(buffer)
            if (receiveBuffer!!.available() >= Integer.BYTES) {
                val rpos = receiveBuffer!!.rpos()
                val len = receiveBuffer!!.int
                // Protect against malicious or corrupted packets
                if (len < 0) {
                    throw StreamCorruptedException("Illogical message length: $len")
                }
                receiveBuffer!!.rpos(rpos)
                if (receiveBuffer!!.available() >= Integer.BYTES + len) {
                    message = ByteArrayBuffer(receiveBuffer!!.bytes)
                    receiveBuffer!!.compact()
                }
            }
        }
        if (message != null) {
            synchronized(lock) {
                messages.offer(message)
                lock.notifyAll()
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (open.getAndSet(false)) {
            unixSocketChannel.close()
        }

        // make any waiting thread aware of the closure
        synchronized(lock) { lock.notifyAll() }

        val pumperFuture = pumper
        if (pumperFuture != null && !pumperFuture.isDone) {
            pumperFuture.cancel(true)
        }

        super.close()
    }

    @Synchronized
    @Throws(IOException::class)
    override fun request(buffer: Buffer): Buffer {
        val wpos = buffer.wpos()
        buffer.wpos(0)
        buffer.putInt(wpos - 4.toLong())
        buffer.wpos(wpos)
        synchronized(lock) {
            unixSocketChannel.write(buffer.array(), buffer.rpos(), buffer.available())
            /*
            Channels.newOutputStream(unixSocketChannel).use { writer ->
                writer.write(buffer.array(), buffer.rpos(), buffer.available())
            }
             */
            return waitForMessageBuffer()
        }
    }

    // NOTE: assumes messages lock is obtained prior to calling this method
    @Throws(IOException::class)
    private fun waitForMessageBuffer(): Buffer {
        val mgr = factoryManager
        var idleTimeout = PropertyResolverUtils.getLongProperty(
            mgr, MESSAGE_POLL_FREQUENCY, DEFAULT_MESSAGE_POLL_FREQUENCY
        )
        if (idleTimeout <= 0L) {
            idleTimeout = DEFAULT_MESSAGE_POLL_FREQUENCY
        }
        val traceEnabled = log.isTraceEnabled
        var count = 1
        while (true) {
            if (!isOpen) {
                throw SshException("Client is being closed")
            }
            if (!messages.isEmpty()) {
                return messages.poll()
            }
            if (traceEnabled) {
                log.trace("waitForMessageBuffer({}) wait iteration #{}", this, count)
            }
            try {
                lock.wait(idleTimeout)
            } catch (e: InterruptedException) {
                throw (InterruptedIOException("Interrupted while waiting for messages at iteration #$count").initCause(e) as IOException)
            }
            count++
        }
    }


    override fun toString(): String {
        return javaClass.simpleName + "[socket=" + authSocket + "]"
    }

    companion object {
        /**
         * Time to wait for new incoming messages before checking if the client is still active
         */
        const val MESSAGE_POLL_FREQUENCY = "agent-client-message-poll-time"

        /**
         * Default value for {@value #MESSAGE_POLL_FREQUENCY}
         */
        val DEFAULT_MESSAGE_POLL_FREQUENCY = TimeUnit.MINUTES.toMillis(2L)
    }

    init {
        try {
//            val path = File(authSocket)
//            val address = UnixSocketAddress(path)
//            unixSocketChannel = UnixSocketChannel.open(address)
//            if (!unixSocketChannel.isOpen || !unixSocketChannel.isConnected) {
//                throw IOException("failed to connect to $address")
//            }
            // unixSocketChannel = JNAUSocketFactory().open(authSocket)
            unixSocketChannel = UnixSocket(authSocket)
            // val aprLibInstance = AprLibrary.getInstance()
            // pool = Pool.create(aprLibInstance.rootPool)
            // handle = Local.create(authSocket, pool)
//            val result: Int = Local.connect(handle, 0)
//            if (result != Status.APR_SUCCESS) {
//                throwException(result)
//            }
            val service = executorService
            pumper = service.submit(this)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw SshException(e)
        }
    }
}