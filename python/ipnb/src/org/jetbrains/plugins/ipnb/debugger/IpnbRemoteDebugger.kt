// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.jetbrains.python.debugger.IPyDebugProcess
import com.jetbrains.python.debugger.pydev.RemoteDebugger
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel

class IpnbRemoteDebugger(debugProcess: IPyDebugProcess, private val debuggerTransport: IpnbDebuggerTransport,
                         val codePanel: IpnbCodePanel) : RemoteDebugger(debugProcess,
                                                                        debuggerTransport) {

  @Throws(Exception::class)
  override fun waitForConnect() {
    debuggerTransport.waitForConnect()
  }

  override fun run() {
    val connectionManager = IpnbConnectionManager.getInstance(myDebugProcess.session.project)
    connectionManager.executeCellAddConnection(codePanel, debuggerTransport.connectionId)
  }
}