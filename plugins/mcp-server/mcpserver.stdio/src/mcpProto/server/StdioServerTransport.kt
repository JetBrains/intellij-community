package com.intellij.mcpserver.stdio.mcpProto.server

import com.intellij.mcpserver.stdio.mcpProto.KotlinLogging
import com.intellij.mcpserver.stdio.mcpProto.JSONRPCMessage
import com.intellij.mcpserver.stdio.mcpProto.shared.AbstractTransport
import com.intellij.mcpserver.stdio.mcpProto.shared.ReadBuffer
import com.intellij.mcpserver.stdio.mcpProto.shared.serializeMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

/**
 * A server transport that communicates with a client via standard I/O.
 *
 * Reads from System.in and writes to System.out.
 */
@OptIn(ExperimentalAtomicApi::class)
public class StdioServerTransport(
    private val inputStream: Source,
    outputStream: Sink
) : AbstractTransport() {
    private val logger = KotlinLogging.logger {}

    private val readBuffer = ReadBuffer()
    private val initialized: AtomicBoolean = AtomicBoolean(false)
    private var readingJob: Job? = null
    private var sendingJob: Job? = null

    private val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()
    private val scope = CoroutineScope(coroutineContext)
    private val readChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val writeChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
    private val outputWriter = outputStream.buffered()

    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error("StdioServerTransport already started!")
        }

        // Launch a coroutine to read from stdin
        readingJob = scope.launch {
            val buf = Buffer()
            try {
                while (isActive) {
                    val bytesRead = inputStream.readAtMostTo(buf, 8192)
                    if (bytesRead == -1L) {
                        // EOF reached
                        break
                    }
                    if (bytesRead > 0) {
                        val chunk = buf.readByteArray()
                        readChannel.send(chunk)
                    }
                }
            } catch (e: Throwable) {
                logger.error(e) { "Error reading from stdin" }
                _onError.invoke(e)
            } finally {
                // Reached EOF or error, close connection
                close()
            }
        }

        // Launch a coroutine to process messages from readChannel
        scope.launch {
            try {
                for (chunk in readChannel) {
                    readBuffer.append(chunk)
                    processReadBuffer()
                }
            } catch (e: Throwable) {
                _onError.invoke(e)
            }
        }

        // Launch a coroutine to handle message sending
        sendingJob = scope.launch {
            try {
                for (message in writeChannel) {
                    val json = serializeMessage(message)
                    outputWriter.writeString(json)
                    outputWriter.flush()
                }
            } catch (e: Throwable) {
                logger.error(e) { "Error writing to stdout" }
                _onError.invoke(e)
            }
        }
    }

    private suspend fun processReadBuffer() {
        while (true) {
            val message = try {
                readBuffer.readMessage()
            } catch (e: Throwable) {
                _onError.invoke(e)
                null
            }

            if (message == null) break
            // Async invocation broke delivery order
            try {
                _onMessage.invoke(message)
            } catch (e: Throwable) {
                _onError.invoke(e)
            }
        }
    }

    override suspend fun close() {
        if (!initialized.compareAndSet(expectedValue = true, newValue = false)) return

        withContext(NonCancellable) {
            writeChannel.close()
            sendingJob?.cancelAndJoin()

            runCatching {
                inputStream.close()
            }.onFailure { logger.warn(it) { "Failed to close stdin" } }

            readingJob?.cancel()

            readChannel.close()
            readBuffer.clear()

            runCatching {
                outputWriter.flush()
                outputWriter.close()
            }.onFailure { logger.warn(it) { "Failed to close stdout" } }

            _onClose.invoke()
        }
    }

    override suspend fun send(message: JSONRPCMessage) {
        writeChannel.send(message)
    }
}
