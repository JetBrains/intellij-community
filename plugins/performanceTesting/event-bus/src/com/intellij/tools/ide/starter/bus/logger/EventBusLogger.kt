package com.intellij.tools.ide.starter.bus.logger

import java.time.LocalTime
import java.time.format.DateTimeFormatter

class EventBusLogger(val name: String) {
  private fun getTime(): String {
    val currentTime = LocalTime.now()
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    return "[${currentTime.format(formatter)}]"
  }

  private fun print(level: String, message: String) {
    println("${getTime()}: $level - $name - $message")
  }

  fun info(message: String) {
    print("INFO", message)
  }

  fun debug(message: String) {
    if (System.getProperty("eventbus.debug", "false").toBoolean())
      print("DEBUG", message)
  }
}