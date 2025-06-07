package com.intellij.mcpserver.stdio.mcpProto.shared

import io.ktor.utils.io.core.writeFully
import com.intellij.mcpserver.stdio.mcpProto.JSONRPCMessage
import kotlinx.io.Buffer
import kotlinx.io.indexOf
import kotlinx.io.readString

/**
 * Buffers a continuous stdio stream into discrete JSON-RPC messages.
 */
public class ReadBuffer {
    private val buffer: Buffer = Buffer()

    public fun append(chunk: ByteArray) {
        buffer.writeFully(chunk)
    }

    public fun readMessage(): JSONRPCMessage? {
        if (buffer.exhausted()) return null
        var lfIndex = buffer.indexOf('\n'.code.toByte())
        val line = when (lfIndex) {
            -1L -> return null
            0L -> {
                buffer.skip(1)
                return null
            }

            else -> {
                var skipBytes = 1
                if (buffer[lfIndex - 1] == '\r'.code.toByte()) {
                    lfIndex -= 1
                    skipBytes += 1
                }
                val string = buffer.readString(lfIndex)
                buffer.skip(skipBytes.toLong())
                string
            }
        }
        return deserializeMessage(line)
    }

    public fun clear() {
        buffer.clear()
    }
}

internal fun deserializeMessage(line: String): JSONRPCMessage {
    return McpJson.decodeFromString<JSONRPCMessage>(line)
}

internal fun serializeMessage(message: JSONRPCMessage): String {
    return McpJson.encodeToString(message) + "\n"
}

