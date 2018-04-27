package com.jetbrains.python.console.thrift

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.apache.thrift.transport.TTransport
import java.io.PipedInputStream
import java.io.PipedOutputStream

abstract class TNettyCumulativeTransport : TTransport() {
  val outputStream: PipedOutputStream = PipedOutputStream()

  private val pipedInputStream: PipedInputStream = PipedInputStream(outputStream)

  private val messageBuffer: ByteBuf = Unpooled.buffer()

  override fun open() {
    // TODO should we do smth?
  }

  final override fun write(buf: ByteArray, off: Int, len: Int) {
    messageBuffer.writeBytes(buf, off, len)
  }

  final override fun flush() {
    val length = messageBuffer.readableBytes()
    val content = ByteArray(length)
    messageBuffer.readBytes(content)
    messageBuffer.clear()

    writeMessage(content)
  }

  abstract fun writeMessage(content: ByteArray)

  override fun close() {
    // TODO we are not the owners of the channel, so just do nothing. Or not?
  }

  final override fun read(buf: ByteArray, off: Int, len: Int): Int = pipedInputStream.read(buf, off, len)
}