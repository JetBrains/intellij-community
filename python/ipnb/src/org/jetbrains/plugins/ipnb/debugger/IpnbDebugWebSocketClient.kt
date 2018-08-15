// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import org.java_websocket.drafts.Draft
import org.jetbrains.plugins.ipnb.protocol.IpnbConnection
import org.jetbrains.plugins.ipnb.protocol.IpnbWebSocketClient
import java.net.URI

class IpnbDebugWebSocketClient(serverUri: URI, draft: Draft, connection: IpnbConnection) : IpnbWebSocketClient(serverUri, draft,
                                                                                                               connection) {
}