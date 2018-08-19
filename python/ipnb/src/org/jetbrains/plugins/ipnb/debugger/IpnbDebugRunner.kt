// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.console.PythonDebugLanguageConsoleView
import com.jetbrains.python.debugger.PyDebugRunner
import com.jetbrains.python.run.PythonCommandLineState
import icons.PythonIcons

class IpnbDebugRunner : PyDebugRunner() {
  companion object {

    @Throws(ExecutionException::class)
    fun createDebugSession(project: Project): XDebugSession {
      val serverSocket = PythonCommandLineState.createServerSocket()

      return XDebuggerManager.getInstance(project).startSessionAndShowTab(
        "Jupyter Notebook Debugger", PythonIcons.Python.Python, null, false, object : XDebugProcessStarter() {
        override fun start(session: XDebugSession): XDebugProcess {
          val debugConsoleView = PythonDebugLanguageConsoleView(project, null)
          val ipnbDebugProcessHandler = IpnbDebugProcessHandler()

          val ipnbDebugProcess = IpnbDebugProcess(session, serverSocket, debugConsoleView,
                                                  ipnbDebugProcessHandler)

          ipnbDebugProcess.connect()

          return ipnbDebugProcess
        }
      })
    }
  }
}
