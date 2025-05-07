package com.intellij.terminal.backend.rpc

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Send channel wrapper that allows forwarding elements to [original], but prohibits closing it.
 * So, clients still can install [invokeOnClose] handler, use [isClosedForSend] and call [close], but it won't
 * close the original channel.
 */
internal class NonClosableSendChannel<T>(private val original: SendChannel<T>) : SendChannel<T> by original {
  private val isClosed = AtomicBoolean(false)
  private val invokeOnCloseHandler = AtomicReference<((Throwable?) -> Unit)?>(null)

  @DelicateCoroutinesApi
  override val isClosedForSend: Boolean
    get() = isClosed.get() || original.isClosedForSend

  override fun close(cause: Throwable?): Boolean {
    return if (isClosed.compareAndSet(false, true)) {
      val handler = invokeOnCloseHandler.get()
      if (handler != null) {
        handler(cause)
      }
      true
    }
    else false
  }

  override fun invokeOnClose(handler: (Throwable?) -> Unit) {
    if (!invokeOnCloseHandler.compareAndSet(null, handler)) {
      error("Another handler is already registered")
    }
  }
}