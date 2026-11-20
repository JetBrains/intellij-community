// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ketraterm

import com.intellij.diagnostic.rethrowControlFlowException
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import io.github.ketraterm.transport.TerminalConnector
import io.github.ketraterm.transport.TerminalConnectorListener
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import org.jetbrains.plugins.terminal.ShellTerminalWidget

/** Connects the IntelliJ process host to the KetraTerm byte stream. */
internal class KetraTermTtyConnector(
  private val connector: TtyConnector,
  private val outputLock: ReentrantLock,
  private val afterOutput: () -> Unit,
) : TerminalConnector {
  private val started = AtomicBoolean()
  private val closed = AtomicBoolean()
  private val process = ShellTerminalWidget.getProcessTtyConnector(connector)?.process

  override fun start(listener: TerminalConnectorListener) {
    check(!closed.get() && started.compareAndSet(false, true)) { "The terminal connector already started or closed" }
    thread(name = "KetraTerm PTY reader", isDaemon = true) {
      try {
        val stream = process?.inputStream
        if (stream != null) {
          val bytes = ByteArray(16 * 1024)
          while (!closed.get()) {
            val count = stream.read(bytes)
            if (count < 0) break
            if (count == 0) continue
            deliver(listener, bytes, count)
          }
        }
        else {
          readCharacters(listener)
        }
        if (!closed.get()) listener.onClosed(process?.let { connector.waitFor() })
      }
      catch (error: Throwable) {
        rethrowControlFlowException(error)
        if (!closed.get()) listener.onError(error)
      }
    }
  }

  private fun readCharacters(listener: TerminalConnectorListener) {
    val chars = CharBuffer.allocate(4097)
    val bytes = ByteBuffer.allocate(3 * chars.capacity())
    val encoder = Charsets.UTF_8.newEncoder()
      .onMalformedInput(CodingErrorAction.REPLACE)
      .onUnmappableCharacter(CodingErrorAction.REPLACE)
    while (!closed.get()) {
      val count = connector.read(chars.array(), chars.position(), chars.remaining())
      val end = count < 0
      if (!end) chars.position(chars.position() + count)
      chars.flip()
      val result = encoder.encode(chars, bytes, end)
      if (result.isError) result.throwException()
      deliver(listener, bytes.array(), bytes.position())
      bytes.clear()
      chars.compact()
      if (end) break
    }
  }

  private fun deliver(listener: TerminalConnectorListener, bytes: ByteArray, length: Int) {
    if (length == 0) return
    outputLock.withLock {
      listener.onBytes(bytes, 0, length)
      afterOutput()
    }
  }

  override fun write(bytes: ByteArray, offset: Int, length: Int) {
    if (offset == 0 && length == bytes.size) connector.write(bytes)
    else connector.write(bytes.copyOfRange(offset, offset + length))
  }

  override fun resize(columns: Int, rows: Int) {
    connector.resize(TermSize(columns, rows))
  }

  override fun close() {
    if (closed.compareAndSet(false, true)) connector.close()
  }
}
