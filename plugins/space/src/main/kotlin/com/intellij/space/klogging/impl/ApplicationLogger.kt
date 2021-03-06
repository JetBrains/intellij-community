// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.klogging.impl

import com.intellij.openapi.diagnostic.Logger
import libraries.klogging.BaseLogger

class ApplicationLogger(private val logger: Logger) : BaseLogger {

  override val isTraceEnabled: Boolean get() = logger.isTraceEnabled
  override val isDebugEnabled: Boolean get() = logger.isDebugEnabled
  override val isInfoEnabled: Boolean get() = true
  override val isWarnEnabled: Boolean get() = true
  override val isErrorEnabled: Boolean get() = true

  override fun trace(message: Any?) {
    logger.trace(message.toString())
  }

  override fun debug(message: Any?) {
    logger.debug(message.toString())
  }

  override fun info(message: Any?) {
    logger.info(message.toString())
  }

  override fun warn(message: Any?) {
    logger.warn(message.toString())
  }

  override fun trace(t: Throwable, message: Any?) {
    logger.trace(message.toString())
    logger.trace(t)
  }

  override fun debug(t: Throwable, message: Any?) {
    logger.debug(message.toString(), t)
  }

  override fun info(t: Throwable, message: Any?) {
    logger.info(message.toString(), t)
  }

  override fun warn(t: Throwable, message: Any?) {
    logger.warn(message.toString(), t)
  }

  override fun error(message: Any?) {
    logger.error(message.toString())
  }

  override fun error(t: Throwable, message: Any?) {
    logger.error(message.toString(), t)
  }

}
