// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.util.text.StringUtil
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.console.PyConsoleDebugProcess
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.helpersPro.PyHelpersProLocator
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel
import java.lang.StringBuilder
import java.net.ServerSocket

class IpnbDebugProcess(session: XDebugSession,
                       serverSocket: ServerSocket,
                       executionConsole: ExecutionConsole,
                       myIpnbDebugProcessHandler: IpnbDebugProcessHandler) : PyDebugProcess(session, serverSocket,
                                                                                            executionConsole,
                                                                                            myIpnbDebugProcessHandler,
                                                                                            false) {
  init {
    myPositionConverter = IpnbPositionConverter(project)
  }

  private val myLocalPort = serverSocket.localPort

  private fun createPydevConnectionCommand(portToConnect: Int): String {
    val command = StringBuilder()
    command.append("import sys\n")
    command.append("sys.path.append('")
    command.append(StringUtil.escapeCharCharacters(PythonHelpersLocator.getHelpersRoot().path + "/pydev"))
    command.append("')\n")
    command.append("sys.path.append('")
    command.append(StringUtil.escapeCharCharacters(PyHelpersProLocator.getHelpersProRoot().path + "/jupyter_debug"))
    command.append("')\n")
    command.append("import pydev_jupyter_utils\n")
    command.append("pydev_jupyter_utils.attach_to_debugger(")
    command.append(portToConnect)
    command.append(")\n")

    return command.toString()
  }

  fun connect(connectionId: String, codePanel: IpnbCodePanel) {
    val portToConnect = myLocalPort
    // TODO: Remote case

    val optionsMap = PyConsoleDebugProcess.makeDebugOptionsMap(session)
    val envs = PyConsoleDebugProcess.getDebuggerEnvs(session)

    val connectionManager = IpnbConnectionManager.getInstance(project)
    connectionManager.executeCode(codePanel, connectionId, createPydevConnectionCommand(portToConnect))
  }

  private fun setLatestCellId(cellId: Int): String {
    val command = StringBuilder()
    command.append("import pydev_jupyter_utils\n")
    command.append("pydev_jupyter_utils.set_latest_cell_id('$cellId')\n")
    return command.toString()
  }

  fun updateLatestCellId(connectionId: String, codePanel: IpnbCodePanel, cellId: Int) {
    val connectionManager = IpnbConnectionManager.getInstance(project)
    connectionManager.executeCode(codePanel, connectionId, setLatestCellId(cellId))
  }

}
