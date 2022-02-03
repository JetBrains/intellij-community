package com.intellij.ide.starter.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun getFormattedTime() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss"))

fun log(message: String, printerFunc: (String) -> Unit) {
  if (message.isEmpty()) {
    printerFunc(message)
  }
  else {
    printerFunc("[${getFormattedTime()}]: $message")
  }
}

fun logOutput() {
  logOutput("")
}

fun logOutput(any: Any?) {
  logOutput(any?.toString() ?: "null")
}

fun logOutput(message: String) = log(message) { println(it) }

fun logError(any: Any?) = log(any?.toString() ?: "null") { System.err.println(it) }

fun logError(message: String) = log(message) { System.err.println(it) }
fun logError(message: String, t: Throwable) {
  log(message) { System.err.println(it) }
  logError(t)
}