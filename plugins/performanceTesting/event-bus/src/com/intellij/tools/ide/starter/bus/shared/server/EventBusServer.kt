package com.intellij.tools.ide.starter.bus.shared.server

interface EventBusServer {
  val port: Int
  fun startServer()
  fun endServer()
  fun updatePort(): Boolean
}