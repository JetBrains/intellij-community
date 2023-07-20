// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CompletableFuture

/**
 * Base completion manager for Zsh and Bash
 */
abstract class BaseShCompletionManager(session: TerminalSession) : TerminalCompletionManager(session) {
  override fun invokeCompletion(command: String) {
    session.terminalStarter.sendString(command + "\t", false)
  }

  protected abstract fun doResetPrompt(disposable: Disposable)

  override fun resetPrompt() {
    val model = session.model
    val disposable = Disposer.newDisposable()
    try {
      doResetPrompt(disposable)
    }
    catch (t: Throwable) {
      thisLogger().error("Prompt is broken after completion. Text buffer:\n"
                         + model.withContentLock { model.getAllText() }, t)
    }
    finally {
      Disposer.dispose(disposable)
      model.commandExecutionSemaphore.up()
    }
  }

  protected fun checkTerminalContent(disposable: Disposable, check: () -> Boolean): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()
    session.model.addContentListener(object : TerminalModel.ContentListener {
      override fun onContentChanged() {
        if (check()) {
          future.complete(true)
        }
      }
    }, disposable)
    return future
  }
}