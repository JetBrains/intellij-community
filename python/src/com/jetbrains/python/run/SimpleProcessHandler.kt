// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.KillableProcess
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.io.OutputStream

@ApiStatus.Internal
class SimpleProcessHandler : ProcessHandler(), KillableProcess, ColoredTextAcceptor {
  private val myAnsiEscapeDecoder = AnsiEscapeDecoder()

  /**
   * Asked on the first destroy request. `true` means that the owner started a graceful shutdown.
   * The handler then reports the terminating state without a destroy, and [killProcess] ends it.
   */
  @Volatile
  var requestGracefulDestroy: () -> Boolean = { false }

  @Volatile
  private var isGracefulDestroyPending = false

  override fun notifyTextAvailable(text: String, outputType: Key<*>) {
    myAnsiEscapeDecoder.escapeText(text, outputType, this)
  }

  override fun destroyProcess() {
    if (!isGracefulDestroyPending && !isProcessTerminating && !isProcessTerminated && requestGracefulDestroy()) {
      isGracefulDestroyPending = true
      return
    }
    super.destroyProcess()
  }

  override fun isProcessTerminating(): Boolean = (isGracefulDestroyPending && !isProcessTerminated) || super.isProcessTerminating()

  override fun destroyProcessImpl() {
    notifyProcessTerminated(0)
  }

  override fun detachProcessImpl() {
    notifyProcessDetached()
  }

  override fun detachIsDefault(): Boolean = false

  override fun getProcessInput(): OutputStream? = null

  override fun canKillProcess(): Boolean = true

  /** Runs the destroy that a graceful shutdown skipped. */
  override fun killProcess() {
    super.destroyProcess()
  }

  override fun coloredTextAvailable(text: String, attributes: Key<*>) {
    super.notifyTextAvailable(text, attributes)
  }
}