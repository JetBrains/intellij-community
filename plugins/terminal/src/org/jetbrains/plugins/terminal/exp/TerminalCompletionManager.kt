// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

abstract class TerminalCompletionManager(protected val session: TerminalSession) {
  @RequiresBackgroundThread
  fun waitForTerminalLock() {
    val model = session.model
    ProgressIndicatorUtils.awaitWithCheckCanceled(model.commandExecutionSemaphore, ProgressManager.getInstance().progressIndicator)
    model.commandExecutionSemaphore.down()
  }

  abstract fun invokeCompletion(command: String)

  @RequiresBackgroundThread
  abstract fun resetPrompt()

  companion object {
    val KEY: Key<TerminalCompletionManager> = Key.create("TerminalCompletionManager")
  }
}