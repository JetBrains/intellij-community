// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Error that is interested to user.
 * Such errors usually stem from user errors or external process errors (i.e., permissions, network connections).
 * Those are *not* NPEs nor OOBs nor various assertions.
 * Do *not* use `catch(Exception)` or `runCatching` with this class.
 *
 * Use [message] to provide user-readable error description i.e "could not connect to the Internet".
 * Upper levels will add additional information there.
 *
 * Most probably you will send this error to [ErrorSink].
 */
sealed class PyError(message: @NlsSafe String) {
  private val _messages = CopyOnWriteArrayList<@Nls String>(listOf(message))
  val message: @Nls String get() = _messages.reversed().joinToString("\n")

  /**
   * To be used by [getOr], see it for more info
   */
  fun addMessage(message: @Nls String) {
    _messages.add(message)
  }

  override fun toString(): String = message
}
