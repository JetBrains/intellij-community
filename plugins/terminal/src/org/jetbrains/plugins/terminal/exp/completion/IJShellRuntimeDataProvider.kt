// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import org.jetbrains.plugins.terminal.exp.TerminalSession
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class IJShellRuntimeDataProvider(private val session: TerminalSession) : ShellRuntimeDataProvider {
  override fun getFilesFromDirectory(path: String): List<String> {
    if (session.shellIntegration?.shellType != ShellType.ZSH) {
      return emptyList()
    }
    val requestId = CUR_ID.getAndIncrement()
    val command = "$GET_DIRECTORY_FILES_COMMAND $requestId $path"
    val result = executeCommandBlocking(requestId, command)
    return result.split("\n")
  }

  private fun executeCommandBlocking(reqId: Int, command: String): String {
    val resultFuture = CompletableFuture<String>()
    val disposable = Disposer.newDisposable()
    try {
      session.addCommandListener(object : ShellCommandListener {
        override fun generatorFinished(requestId: Int, result: String) {
          if (requestId == reqId) {
            resultFuture.complete(result)
          }
        }
      }, disposable)

      session.executeCommand(command)
      return ApplicationUtil.runWithCheckCanceled(resultFuture, ProgressManager.getInstance().progressIndicator)
    }
    finally {
      Disposer.dispose(disposable)
      val model = session.model
      model.withContentLock { model.clearAll() }
    }
  }

  companion object {
    private const val GET_DIRECTORY_FILES_COMMAND = "__jetbrains_intellij_get_directory_files"
    private val CUR_ID = AtomicInteger(0)
  }
}