// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.internal


class ThreadLocalTroubleCollector {

  private val collected: ThreadLocal<StringBuilder> = ThreadLocal()

  fun <T> withCollectingInfo(body: () -> T): Pair<T, String> {
    val out = StringBuilder()
    val result = collected.withValue(out) { body() }
    return result to out.toString()
  }
  
  @Suppress("SSBasedInspection")
  val logger: Logger = Logger()
  
  inner class Logger {

    fun log(message: () -> String) {
      val stringBuilder = collected.get() ?: return
      stringBuilder.appendLine(message())
    }  
    
    fun <T> logAndNull(message: () -> String): T? {
      log(message)
      return null
    }
    
  }

}