package com.jetbrains.python.console.transport

internal class DirectedMessage(val direction: MessageDirection, val content: ByteArray) {
  internal enum class MessageDirection { REQUEST, RESPONSE }
}