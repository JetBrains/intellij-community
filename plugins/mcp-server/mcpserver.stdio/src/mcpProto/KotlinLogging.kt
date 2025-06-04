package com.intellij.mcpserver.stdio.mcpProto

object KotlinLogging {
  enum class Level { TRACE, DEBUG, INFO, WARN, ERROR }

  fun logger(builder: () -> Unit): KotlinLogging = this

  fun debug(t: Throwable? = null, function: () -> String?) {
    log(Level.DEBUG, t, function)
  }

  fun info(t: Throwable? = null, function: () -> String?) {
    log(Level.INFO, t, function)
  }

  fun error(t: Throwable? = null, function: () -> String?) {
    log(Level.ERROR, t, function)
  }

  fun trace(t: Throwable? = null, function: () -> String?) {
    log(Level.TRACE, t, function)
  }

  fun warn(t: Throwable? = null, function: () -> String?) {
    log(Level.WARN, t, function)
  }

  fun log(level: Level, t: Throwable? = null, function: () -> String?) {
    if (t != null) {
      System.err.println("$level: ${function()}\r\n${t.stackTraceToString()}\r\n")
    }
    else {
      System.err.println("$level: ${function()}")
    }
  }
}