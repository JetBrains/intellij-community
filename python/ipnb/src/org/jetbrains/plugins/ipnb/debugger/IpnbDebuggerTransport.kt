// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.openapi.project.Project
import com.jetbrains.python.debugger.pydev.ProtocolFrame
import com.jetbrains.python.debugger.pydev.transport.DebuggerTransport
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListenerBase
import java.util.concurrent.TimeUnit

class IpnbDebuggerTransport(val project: Project, private val codePanel: IpnbCodePanel) : DebuggerTransport {
  private var debugConnection: IpnbConnection? = null
  var connectionId: String = IpnbDebuggerTransport.DEBUG_CONNECTION_PREFIX

  init {
    val connectionManager = IpnbConnectionManager.getInstance(project)
    if (!connectionManager.hasConnection(connectionId)) {
      connectionManager.startConnection(codePanel, connectionId, object : IpnbConnectionListenerBase() {
        override fun onOpen(connection: IpnbConnection) {
          debugConnection = connection
        }

        override fun onOutput(connection: IpnbConnection, parentMessageId: String) {

        }

        override fun onPayload(payload: String?, parentMessageId: String) {

        }

        override fun onFinished(connection: IpnbConnection, parentMessageId: String) {

        }
      })
    }
  }

  companion object {
    const val DEBUG_CONNECTION_PREFIX: String = "Debug"
  }

  override fun sendFrame(frame: ProtocolFrame): Boolean {
    return true
  }


  override fun waitForConnect() {
    while (debugConnection == null) {
      TimeUnit.MILLISECONDS.sleep(500)
    }
  }

  override fun close() {
    debugConnection?.close()
    debugConnection = null
  }

  override fun isConnecting(): Boolean {
    return false
  }

  override fun isConnected(): Boolean {
    return debugConnection != null
  }

  override fun disconnect() {
    debugConnection?.interrupt()
  }
}