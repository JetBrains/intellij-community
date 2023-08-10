// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyConsoleProcessHandlers")

package com.jetbrains.python.console

import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.ui.UIUtil

@JvmField
val pydevConsoleCommunicationKey: Key<PydevConsoleCommunication> = Key("PydevConsoleCommunication")

internal fun configureProcessHandlerForPythonConsole(processHandler: ProcessHandler,
                                                     consoleView: PythonConsoleView,
                                                     consoleCommunication: PydevConsoleCommunication) {
  Disposer.register(consoleView) {
    if (!processHandler.isProcessTerminated) {
      processHandler.destroyProcess()
    }
  }
  processHandler.addProcessListener(object : ProcessListener {
    override fun processTerminated(event: ProcessEvent) {
      doCloseCommunication(consoleCommunication)
    }
  })

  if (processHandler is KillableProcessHandler) {
    processHandler.setShouldKillProcessSoftly(false)
  }

  processHandler.putUserData(pydevConsoleCommunicationKey, consoleCommunication)
}

private fun doCloseCommunication(consoleCommunication: PydevConsoleCommunication) {
  UIUtil.invokeAndWaitIfNeeded {
    try {
      consoleCommunication.close()
      Thread.sleep(300)
    }
    catch (e: Exception) {
      // Ignore
    }
  }

  // waiting for REPL communication before destroying process handler
}