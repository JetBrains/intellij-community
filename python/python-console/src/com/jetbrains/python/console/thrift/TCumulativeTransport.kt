package com.jetbrains.python.console.thrift

import com.intellij.openapi.diagnostic.Logger
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.apache.thrift.transport.TTransport
import java.io.PipedInputStream
import java.io.PipedOutputStream

abstract class TCumulativeTransport : TTransport() {
  val outputStream: PipedOutputStream = PipedOutputStream()

  private val pipedInputStream: PipedInputStream = PipedInputStream(outputStream)

  private val messageBuffer: ByteBuf = Unpooled.buffer()

  override fun open() {}

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
    LOG.debug("Closing cumulative transport")

    outputStream.close()
    pipedInputStream.close()
  }

  final override fun read(buf: ByteArray, off: Int, len: Int): Int = pipedInputStream.read(buf, off, len)

  companion object {
    val LOG = Logger.getInstance(TCumulativeTransport::class.java)
  }
}