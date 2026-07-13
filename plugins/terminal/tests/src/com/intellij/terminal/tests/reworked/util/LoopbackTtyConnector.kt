// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import java.io.PipedReader
import java.io.PipedWriter
import java.util.concurrent.CountDownLatch

/**
 * In-memory [TtyConnector] for terminal tests that need no shell process.
 *
 * Text passed to [write] is fed back through [read] into the terminal emulator as if a shell
 * produced it, so a test can drive the emulator with raw ANSI/VT sequences.
 *
 * The `ByteArray` [write] overload carries the emulator's own responses (device attributes,
 * cursor reports) and is ignored.
 */
internal class LoopbackTtyConnector : TtyConnector {
  private val writer = PipedWriter()

  // The pipe buffer is large enough that test writes never block waiting for the emulator to catch up.
  private val reader = PipedReader(writer, 65_536)

  private val closed = CountDownLatch(1)

  /**
   * Test injection point: appends [string] to the buffer that [read] drains.
   * Feeding, for example, `"[31mRED[0m"` makes the emulator render red "RED".
   */
  override fun write(string: String?) {
    if (string.isNullOrEmpty()) return
    writer.write(string)
  }

  /** Emulator responses (DA, cursor reports, etc.). Not relevant for these tests. */
  override fun write(bytes: ByteArray) {
    // no-op
  }

  override fun read(buf: CharArray, offset: Int, length: Int): Int = reader.read(buf, offset, length)

  override fun ready(): Boolean = reader.ready()

  override fun isConnected(): Boolean = closed.count > 0

  override fun waitFor(): Int {
    closed.await()
    return 0
  }

  override fun getName(): String = "loopback"

  override fun resize(termSize: TermSize) {
    // no-op
  }

  override fun close() {
    closed.countDown()
    // After the writer is closed, [read] drains the buffered chars and then returns -1,
    // so TtyBasedArrayDataStream stops the emulation.
    writer.close()
  }
}
