// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.python.debugger.PyDebugProcess

class IpnbDebugProcess(session: XDebugSession,
                       debuggerFactory: DebuggerFactory,
                       executionConsole: ExecutionConsole,
                       processHandler: ProcessHandler?) : PyDebugProcess(session, debuggerFactory, executionConsole, processHandler) {
  private var myWaitingForConnection: Boolean = false

  override fun waitForConnection(connectionMessage: String?, connectionTitle: String?) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(session.getProject(), connectionTitle!!, false) {
      override fun run(indicator: ProgressIndicator) {

        indicator.text = connectionMessage
        try {
          myWaitingForConnection = true
          myDebugger.waitForConnect()
          myWaitingForConnection = false

          init()
          myDebugger.run()
        }
        catch (e: Exception) {
          myWaitingForConnection = false
          processHandler.destroyProcess()

          if (shouldLogConnectionException(e)) {
            //            LOG.error(e)
          }
        }
      }
    })
  }
}