// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.execution.ui.ExecutionConsole
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.python.console.PyConsoleDebugProcess
import com.jetbrains.python.debugger.PyDebugProcess
import java.net.ServerSocket

class IpnbDebugProcess(session: XDebugSession,
                       serverSocket: ServerSocket,
                       executionConsole: ExecutionConsole,
                       private val myIpnbDebugProcessHandler: IpnbDebugProcessHandler) : PyDebugProcess(session, serverSocket,
                                                                                                        executionConsole,
                                                                                                        myIpnbDebugProcessHandler,
                                                                                                        false) {
  private val myLocalPort = serverSocket.localPort

  fun connect() {
    val portToConnect = myLocalPort
    // TODO: Remote case

    val optionsMap = PyConsoleDebugProcess.makeDebugOptionsMap(session)
    val envs = PyConsoleDebugProcess.getDebuggerEnvs(session)

    // execute connection command in Jupyter Kernel
  }
}
