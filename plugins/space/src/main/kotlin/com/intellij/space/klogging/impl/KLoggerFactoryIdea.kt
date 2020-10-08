// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.klogging.impl

import com.intellij.openapi.diagnostic.Logger
import libraries.klogging.KLogger
import libraries.klogging.KLoggerFactory
import libraries.klogging.LoggerNameSource
import libraries.klogging.loggerNameFromSource
import kotlin.reflect.KClass

object KLoggerFactoryIdea : KLoggerFactory {
  override fun logger(owner: KClass<*>): KLogger = wrapLogger(Logger.getInstance(owner.java))

  override fun logger(owner: Any): KLogger = logger(owner.javaClass.kotlin)

  override fun logger(name: String): KLogger = wrapLogger(Logger.getInstance(name))

  override fun logger(nameSource: LoggerNameSource): KLogger = logger(loggerNameFromSource(nameSource))

  private fun wrapLogger(logger: Logger) = KLogger(wrapWithApplicationLogger(logger))

  fun wrapWithApplicationLogger(logger: Logger): ApplicationLogger = ApplicationLogger(logger)
}
