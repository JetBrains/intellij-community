package org.jetbrains.plugins.textmate.logging

import org.slf4j.Logger

internal class Slf4jTextMateLogger(private val logger: Logger) : TextMateLogger {
  override val isTraceEnabled: Boolean
    get() = logger.isTraceEnabled
  override val isDebugEnabled: Boolean
    get() = logger.isDebugEnabled
  override val isInfoEnabled: Boolean
    get() = logger.isInfoEnabled
  override val isWarnEnabled: Boolean
    get() = logger.isWarnEnabled
  override val isErrorEnabled: Boolean
    get() = logger.isErrorEnabled

  override fun trace(message: () -> String) {
    logger.trace(message())
  }

  override fun trace(t: Throwable?, message: () -> String) {
    logger.trace(message(), t)
  }

  override fun debug(message: () -> String) {
    logger.debug(message())
  }

  override fun debug(t: Throwable?, message: () -> String) {
    logger.debug(message(), t)
  }

  override fun info(message: () -> String) {
    logger.info(message())
  }

  override fun info(t: Throwable?, message: () -> String) {
    logger.info(message(), t)
  }

  override fun warn(message: () -> String) {
    logger.warn(message())
  }

  override fun warn(t: Throwable?, message: () -> String) {
    logger.warn(message(), t)
  }

  override fun error(message: () -> String) {
    logger.error(message())
  }

  override fun error(t: Throwable?, message: () -> String) {
    logger.error(message(), t)
  }
}