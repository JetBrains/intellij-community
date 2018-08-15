// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import org.java_websocket.client.WebSocketClient
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListener
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.*

class IpnbDebugConnection(uri: String,
                          listener: IpnbConnectionListener,
                          token: String?,
                          project: Project,
                          pathToFile: String) : IpnbConnection(uri, listener, token, project, pathToFile) {
  private var myChannelsClient: WebSocketClient? = null
  private var myChannelsThread: Thread? = null

  @Throws(URISyntaxException::class)
  override fun initializeClients() {
    val draft = Draft17WithOrigin()

    myChannelsClient = IpnbDebugWebSocketClient(getChannelsURI(), draft, this)
    myChannelsThread = Thread(myChannelsClient, "IPNB channel client")
    myChannelsThread?.start()
  }

  override fun notifyOpen() {
    if (!myIsOpened) {
      myIsOpened = true
      myListener.onOpen(this)
    }
  }

  override fun execute(code: String): String {
    val messageId = UUID.randomUUID().toString()
    myChannelsClient?.send(Gson().toJson(createExecuteRequest(code, messageId)))
    return messageId
  }

  override fun shutdown() {
    myChannelsClient?.close()
  }

  @Throws(IOException::class, InterruptedException::class)
  override fun close() {
    myChannelsThread?.join()
    shutdownKernel()
  }

  @Throws(URISyntaxException::class)
  fun getChannelsURI(): URI {
    return URI("$webSocketURIBase/channels")
  }

  override fun isAlive(): Boolean {
    return myChannelsClient!!.isOpen()
  }
}