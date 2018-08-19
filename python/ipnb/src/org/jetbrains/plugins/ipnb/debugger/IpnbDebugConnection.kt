// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListener
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionV3
import org.jetbrains.plugins.ipnb.protocol.IpnbDebugWebSocketClient
import java.net.URISyntaxException
import java.util.*

class IpnbDebugConnection(uri: String,
                          listener: IpnbConnectionListener,
                          token: String?,
                          project: Project,
                          pathToFile: String,
                          kernelId: String?,
                          sessionId: String?) : IpnbConnectionV3(uri, listener, token, project, pathToFile, kernelId, sessionId) {

  @Throws(URISyntaxException::class)
  override fun initializeClients() {
    val draft = Draft17WithOrigin()

    myChannelsClient = IpnbDebugWebSocketClient(channelsURI, draft, this)
    myChannelsThread = Thread(myChannelsClient, "IPNB channel client")
    myChannelsThread?.start()
  }


  override fun sendOpenComm() {
    val content = JsonObject()
    content.addProperty("comm_id", "id")
    content.addProperty("target_name", "my_comm_target")
    content.addProperty("data", "{}")

    val message = createMessage("comm_open", UUID.randomUUID().toString(), content, null)
    myChannelsClient.send(Gson().toJson(message))
  }

  override fun sendCommMsg(msg: String?) {
    val content = JsonObject()
    content.addProperty("comm_id", "id")
    content.addProperty("data", "")
    val data = JsonObject()
    data.addProperty("value", msg)
    content.add("data", data)

    val message = createMessage("comm_msg", UUID.randomUUID().toString(), content, null)
    myChannelsClient.send(Gson().toJson(message))
  }
}