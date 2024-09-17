package com.jetbrains.performancePlugin

import com.intellij.openapi.diagnostic.Logger

internal class DriverTestLogger {
  companion object {
    private val LOG: Logger = Logger.getInstance(DriverTestLogger::class.java)

    @JvmStatic
    fun info(text: String) {
      LOG.info(text)
    }
    @JvmStatic
    fun warn(text: String) {
      LOG.warn(text)
    }

    @JvmStatic
    fun warn(t: Throwable) {
      LOG.warn(t)
    }
  }
}