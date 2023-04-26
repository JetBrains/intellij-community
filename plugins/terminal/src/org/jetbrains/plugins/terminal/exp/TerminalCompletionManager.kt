// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class TerminalCompletionManager(private val session: TerminalSession) {
  @RequiresBackgroundThread
  fun waitForTerminalLock() {
    val model = session.model
    ProgressIndicatorUtils.awaitWithCheckCanceled(model.commandExecutionSemaphore, ProgressManager.getInstance().progressIndicator)
    model.commandExecutionSemaphore.down()
  }

  fun invokeCompletion(command: String) {
    session.terminalStarter.sendString(command + "\t", false)
  }

  @RequiresBackgroundThread
  fun resetPrompt(promptText: String) {
    val model = session.model
    val disposable = Disposer.newDisposable()
    try {
      val promptRestoredFuture = checkTerminalContent(disposable) {
        model.getAllText().replace("\n", "").endsWith(promptText)
      }

      // Send Ctrl+U to clear the line with typings
      // There are two invocations: first will clear possible "zsh: do you wish to see all..." question,
      // and the second will clear the typed command
      val command = "\u0015\u0015"
      session.terminalStarter.sendString(command, false)

      promptRestoredFuture.get(3000, TimeUnit.MILLISECONDS)

      val allText = model.withContentLock { model.getAllText() }.replace("\n", "")
      if (promptText.trim() != allText.trim()) {
        // terminal printed the new prompt after the completion output
        val promptLines = promptText.length / model.width + if (promptText.length % model.width > 0) 1 else 0
        model.withContentLock {
          model.clearAllExceptPrompt(promptLines)
        }
      }
    }
    catch (t: Throwable) {
      thisLogger().error("Prompt is broken after completion: promptText: '$promptText'. " +
                         "Text buffer:\n" + model.withContentLock { model.getAllText() }, t)
    }
    finally {
      Disposer.dispose(disposable)
      model.commandExecutionSemaphore.up()
    }
  }

  private fun checkTerminalContent(disposable: Disposable, check: () -> Boolean): CompletableFuture<Boolean> {
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

  companion object {
    val KEY: Key<TerminalCompletionManager> = Key.create("TerminalCompletionManager")
  }
}