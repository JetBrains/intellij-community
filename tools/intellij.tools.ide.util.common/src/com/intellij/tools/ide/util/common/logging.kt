package com.intellij.tools.ide.util.common

fun logOutput() {
  logOutput("")
}

fun logOutput(any: Any?) {
  logOutput(any?.toString() ?: "null")
}

fun logOutput(message: String) {
  println(message)
}

/** The same as [logOutput] but concatenates the string representation of objects */
fun logOutput(vararg objects: Any) {
  println(objects.joinToString(" "))
}

fun logError(any: Any?) {
  System.err.println(any?.toString() ?: "null")
}

fun logError(message: String) {
  System.err.println(message)
}
fun logError(message: String, t: Throwable?) {
  System.err.println(message)
  t?.printStackTrace(System.err)
}