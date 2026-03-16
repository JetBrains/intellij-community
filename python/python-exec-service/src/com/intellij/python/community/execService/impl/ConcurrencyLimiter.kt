// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.community.execService.ConcurrentProcessWeight
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.EnumMap

internal class ConcurrencyLimiter(private val getBucketSize: (ConcurrentProcessWeight) -> Int) {
  companion object {
    private val logger = fileLogger()
  }

  // Registry might not be accessible at the early stage, but once this object is constructed, the app is started, hence there is a registry.
  private val semaphores = EnumMap<ConcurrentProcessWeight, Semaphore>(ConcurrentProcessWeight::class.java)
  private val accessSemaMutex = Mutex()


  suspend fun <T> exec(weight: ConcurrentProcessWeight, code: suspend () -> T): T {
    val sema = accessSemaMutex.withLock { semaphores.getOrPut(weight) { Semaphore(getBucketSize(weight)) } }
    return sema.withPermit {
      if (logger.isTraceEnabled) {
        logger.trace("For $weight perms. left: ${sema.availablePermits}")
      }
      code()
    }
  }
}