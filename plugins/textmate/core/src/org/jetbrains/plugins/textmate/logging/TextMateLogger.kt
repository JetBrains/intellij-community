package org.jetbrains.plugins.textmate.logging

internal interface TextMateLogger {
  val isTraceEnabled: Boolean
  val isDebugEnabled: Boolean
  val isInfoEnabled: Boolean
  val isWarnEnabled: Boolean
  val isErrorEnabled: Boolean

  fun trace(message: () -> String)
  fun debug(message: () -> String)
  fun info(message: () -> String)
  fun warn(message: () -> String)
  fun error(message: () -> String)

  fun trace(t: Throwable?, message: () -> String = { "" })
  fun debug(t: Throwable?, message: () -> String = { "" })
  fun info(t: Throwable?, message: () -> String = { "" })
  fun warn(t: Throwable?, message: () -> String = { "" })
  fun error(t: Throwable?, message: () -> String = { "" })
}