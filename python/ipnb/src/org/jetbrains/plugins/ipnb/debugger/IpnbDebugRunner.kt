// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.console.PythonDebugLanguageConsoleView
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyDebugRunner
import icons.PythonIcons
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel

class IpnbDebugRunner {

  companion object {

    @Throws(ExecutionException::class)
    fun connectToDebugger(project: Project, codePanel: IpnbCodePanel): XDebugSession {

      return XDebuggerManager.getInstance(project).startSessionAndShowTab(
        "Jupyter Notebook Debugger",
        PythonIcons.Python.Python, null, false,
        object : XDebugProcessStarter() {
          override fun start(session: XDebugSession): XDebugProcess {
            val debugConsoleView = PythonDebugLanguageConsoleView(project, null)

            val consoleDebugProcessHandler = IpnbDebugProcessHandler()

            val ipnbDebugProcess = IpnbDebugProcess(
              session, PyDebugProcess.DebuggerFactory { process ->
              IpnbRemoteDebugger(process, IpnbDebuggerTransport(project, codePanel), codePanel)
            }, debugConsoleView, consoleDebugProcessHandler)

            val communication = PyDebugRunner.initDebugConsoleView(project, ipnbDebugProcess, debugConsoleView,
                                                                   consoleDebugProcessHandler, session)

            communication.addCommunicationListener(object : ConsoleCommunicationListener {
              override fun commandExecuted(more: Boolean) {
                session.rebuildViews()
              }

              override fun inputRequested() {}
            })

            debugConsoleView.attachToProcess(consoleDebugProcessHandler)

            return ipnbDebugProcess
          }
        })
    }
  }


}