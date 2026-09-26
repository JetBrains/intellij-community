package org.jetbrains.plugins.textmate

import org.jetbrains.plugins.textmate.atomics.updateAndGet
import org.jetbrains.plugins.textmate.concurrent.TextMateLock
import org.jetbrains.plugins.textmate.concurrent.TextMateThreadLocal
import org.jetbrains.plugins.textmate.concurrent.createTextMateLockJvm
import org.jetbrains.plugins.textmate.concurrent.createTextMateThreadLocalJvm
import org.jetbrains.plugins.textmate.logging.Slf4jTextMateLogger
import org.jetbrains.plugins.textmate.logging.TextMateLogger
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.AtomicReference
import kotlin.reflect.KClass

/**
 * This file contains declarations that delegate to NOT multiplatform-ready code.
 *
 * Implementations from srcJvmMain should neven been references from the common code.
 * Common code should only reference to the declarations from this file.
 *
 * When compiling a multiplatform project, all functions should be replaced with `expect` ones
 * and every target should provide its own implementation.
 */

fun <T> AtomicReference<T>.update(f: (T) -> T) {
  updateAndGet(f)
}

internal fun getLogger(clazz: KClass<*>): TextMateLogger {
  return Slf4jTextMateLogger(LoggerFactory.getLogger(clazz.java))
}

internal fun <T> createTextMateThreadLocal(): TextMateThreadLocal<T> {
  return createTextMateThreadLocalJvm()
}

internal fun createTextMateLock(): TextMateLock {
  return createTextMateLockJvm()
}