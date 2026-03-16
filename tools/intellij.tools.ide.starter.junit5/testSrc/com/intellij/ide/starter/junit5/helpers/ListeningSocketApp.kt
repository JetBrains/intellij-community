package com.intellij.ide.starter.junit5.helpers

import java.net.InetAddress
import java.net.ServerSocket

/**
 * Test-only helper: binds a ServerSocket on the given port and sleeps until killed.
 */
object ListeningSocketApp {
  @JvmStatic
  fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull()
      ?: error("Port argument is required")

    // Bind on loopback to avoid firewall prompts
    ServerSocket(port, 0, InetAddress.getLoopbackAddress()).use {
      // Keep the process alive as long as possible
      while (true) {
        try {
          Thread.sleep(5_000)
        } catch (_: InterruptedException) {
          // continue
        }
      }
    }
  }
}