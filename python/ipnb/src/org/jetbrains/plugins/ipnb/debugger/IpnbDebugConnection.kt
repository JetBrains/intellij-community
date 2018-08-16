// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionListener
import org.jetbrains.plugins.ipnb.protocol.IpnbConnectionV3
import org.jetbrains.plugins.ipnb.protocol.IpnbDebugWebSocketClient
import java.net.URISyntaxException

class IpnbDebugConnection(uri: String,
                          listener: IpnbConnectionListener,
                          token: String?,
                          project: Project,
                          pathToFile: String) : IpnbConnectionV3(uri, listener, token, project, pathToFile) {

  @Throws(URISyntaxException::class)
  override fun initializeClients() {
    val draft = Draft17WithOrigin()

    myChannelsClient = IpnbDebugWebSocketClient(channelsURI, draft, this)
    myChannelsThread = Thread(myChannelsClient, "IPNB channel client")
    myChannelsThread?.start()
  }
}