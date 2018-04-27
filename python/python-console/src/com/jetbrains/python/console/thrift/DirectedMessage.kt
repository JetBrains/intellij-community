package com.jetbrains.python.console.thrift

internal class DirectedMessage(val direction: MessageDirection, val content: ByteArray) {
  internal enum class MessageDirection { REQUEST, RESPONSE }
}