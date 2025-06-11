package com.intellij.mcpserver.impl.util.network

import java.net.InetAddress
import java.net.ServerSocket

internal fun isPortAvailable(port: Int): Boolean {
  return try {
    ServerSocket(port, 0, InetAddress.getLoopbackAddress()).close()
    true
  }
  catch (_: Exception) {
    false
  }
}

internal fun findFirstFreePort(startingPort: Int): Int {
  var port = startingPort
  while (!isPortAvailable(port)) {
    port++
    if (port > 65535) {
      throw IllegalStateException("No free ports available")
    }
  }
  return port
}