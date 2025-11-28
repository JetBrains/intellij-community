package com.intellij.tools.ide.util.common

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

class Logger(clazz: KClass<*>) {
  private val className = clazz.simpleName
  fun info(message: String) = logOutput("[$className]: $message")
  fun error(message: String) = com.intellij.tools.ide.util.common.logError("[$className]: $message")
}

inline fun <reified T> starterLogger() = Logger(T::class)

private fun getFormattedTime() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss"))

// TODO: should we use java logging stack ?
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

/** The same as [logOutput] but concatenates the string representation of objects */
fun logOutput(vararg objects: Any) = log(objects.joinToString(" ")) { println(it) }

fun logError(any: Any?) = log(any?.toString() ?: "null") { System.err.println(it) }

fun logError(message: String) = log(message) { System.err.println(it) }
fun logError(message: String, t: Throwable?) {
  log(message) { System.err.println(it + t?.let { "\n" + t.stackTraceToString() }.orEmpty()) }
}