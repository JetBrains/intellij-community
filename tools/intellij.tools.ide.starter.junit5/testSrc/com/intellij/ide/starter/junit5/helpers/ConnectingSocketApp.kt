package com.intellij.ide.starter.junit5.helpers

import java.net.InetAddress
import java.net.Socket

/**
 * Test-only helper: connects to the given localhost port and keeps the connection open until killed.
 */
object ConnectingSocketApp {
  @JvmStatic
  fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull()
      ?: error("Port argument is required")

    val addr = InetAddress.getLoopbackAddress()
    Socket(addr, port).use {
      // Keep the process alive with an open socket
      while (true) {
        try {
          Thread.sleep(5_000)
        }
        catch (_: InterruptedException) {
        }
      }
    }
  }
}
