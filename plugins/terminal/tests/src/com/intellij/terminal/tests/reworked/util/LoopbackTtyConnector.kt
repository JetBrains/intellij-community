// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * In-memory [TtyConnector] for terminal tests that need no process.
 *
 * Text passed to [feed] is routed through [read] into the terminal emulator as
 * if a process produced it.
 */
internal class LoopbackTtyConnector : TtyConnector {
  private val pipe: InMemoryPipe = InMemoryPipeImpl()

  private val closed = CountDownLatch(1)

  /**
   * Receives each chunk the terminal writes to the process (key/mouse events, query
   * replies). Null — the default — drops the data.
   */
  @Volatile
  var responseHandler: ((ByteArray) -> Unit)? = null

  /**
   * Feeds [text] to the terminal emulator, as if the process printed it.
   */
  fun feed(text: String) {
    pipe.write(text)
  }

  /** Data for the process (key/mouse events; replies to queries) */
  override fun write(string: String) {
    responseHandler?.invoke(string.toByteArray())
  }

  /** Data for the process (key/mouse events; replies to queries) */
  override fun write(bytes: ByteArray) {
    responseHandler?.invoke(bytes)
  }

  override fun read(buf: CharArray, offset: Int, length: Int): Int = pipe.read(buf, offset, length)

  override fun ready(): Boolean = pipe.ready()

  override fun isConnected(): Boolean = closed.count > 0

  override fun waitFor(): Int {
    closed.await()
    return 0
  }

  override fun getName(): String = "loopback"

  /** Sizes passed to [resize], so a test can wait for one via [awaitResize]. */
  private val resizes = LinkedBlockingQueue<TermSize>()

  override fun resize(termSize: TermSize) {
    resizes.add(termSize)
  }

  /** The next size the session asked for, or null if none arrived within [timeoutMillis]. */
  fun awaitResize(timeoutMillis: Long): TermSize? = resizes.poll(timeoutMillis, TimeUnit.MILLISECONDS)

  override fun close() {
    closed.countDown()
    // Stops the emulation: once the pipe is drained, [read] returns -1.
    pipe.close()
  }
}

/**
 * In-memory pipe: [write] and [read] are connected, so one thread reads what another writes, in order.
 *
 * [read] and [ready] must be called from a single reading thread; [write] and [close] may be called
 * from any thread.
 */
private interface InMemoryPipe {
  /** Appends [text] for [read] to return; writing an empty string has no effect. */
  fun write(text: String)

  /** Blocks until some chars arrive; -1 after [close] once the written chars are drained. */
  fun read(buf: CharArray, offset: Int, length: Int): Int

  /** True when [read] would return without blocking. */
  fun ready(): Boolean

  /** After this, [read] returns the remaining written chars and then -1. */
  fun close()
}

/**
 *  [InMemoryPipe] over a [LinkedBlockingQueue]: [write] never blocks, and a waiting reader wakes immediately.
 *
 *  [java.io.PipedWriter]/[java.io.PipedReader] are not used because of their drawbacks:
 *  - a reader blocked on an empty pipe notices new data only on its next one-second
 *    poll — writes do not notify it, only `flush()` does (JDK-8014239);
 *  - the pipe tracks its ends by thread, not by close: a read waiting on an empty pipe throws
 *    `IOException("Write end dead")` once the last writing thread dies, so writes from short-lived
 *    threads (pooled workers, a finished test phase) can break a waiting reader without anything
 *    being closed;
 *  - the buffer is bounded, so a write outrunning the reader blocks — and a writer
 *    blocked on a full buffer can sleep up to a second after space frees (JDK-8073926).
 */
private class InMemoryPipeImpl : InMemoryPipe {
  // Chunks of written text; the empty string marks the end of the stream ([write] never queues one).
  private val chunks: BlockingQueue<String> = LinkedBlockingQueue()

  // current / currentPos / eof are confined to the reading thread.
  private var current: String = ""
  private var currentPos: Int = 0
  private var eof: Boolean = false

  override fun write(text: String) {
    if (text.isNotEmpty()) {
      chunks.put(text)
    }
  }

  override fun read(buf: CharArray, offset: Int, length: Int): Int {
    if (eof) return -1
    if (currentPos == current.length) {
      current = chunks.take()
      currentPos = 0
      if (current.isEmpty()) {
        eof = true
        return -1
      }
    }
    val count = minOf(length, current.length - currentPos)
    current.toCharArray(buf, offset, currentPos, currentPos + count)
    currentPos += count
    return count
  }

  override fun ready(): Boolean {
    return currentPos < current.length || chunks.peek()?.isNotEmpty() == true
  }

  override fun close() {
    chunks.put("")
  }
}
