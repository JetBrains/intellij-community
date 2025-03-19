package com.intellij.tools.ide.starter.bus.logger

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object EventBusLoggerFactory {
  private val loggers = HashMap<String, EventBusLogger>()
  private val loggersLock = ReentrantLock()

  fun getLogger(clazz: Class<*>): EventBusLogger {
    val name = clazz.name

    return loggersLock.withLock {
      loggers.getOrPut(name) { EventBusLogger(name) }
    }
  }
}