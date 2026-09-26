// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.io.OutputStream

@ApiStatus.Internal
class SimpleProcessHandler : ProcessHandler(), ColoredTextAcceptor {
  private val myAnsiEscapeDecoder = AnsiEscapeDecoder()

  override fun notifyTextAvailable(text: String, outputType: Key<*>) {
    myAnsiEscapeDecoder.escapeText(text, outputType, this)
  }

  override fun destroyProcessImpl() {
    notifyProcessTerminated(0)
  }

  override fun detachProcessImpl() {
    notifyProcessDetached()
  }

  override fun detachIsDefault(): Boolean = false

  override fun getProcessInput(): OutputStream? = null

  override fun coloredTextAvailable(text: String, attributes: Key<*>) {
    super.notifyTextAvailable(text, attributes)
  }
}