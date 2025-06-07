// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.python.community.services.systemPython.impl.Cache
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CacheTest {
  private val producerCalled = AtomicInteger()

  private fun createSut(scope: CoroutineScope) = Cache<String, String>(scope, 1.milliseconds) {
    producerCalled.incrementAndGet()
    val element = "$it-value"
    listOf(element)
  }

  @Test
  fun testCache(): Unit = timeoutRunBlocking {
    val sut = createSut(this)
    repeat(20) {
      Assertions.assertArrayEquals(arrayOf("foo-value"), sut.get("foo").toTypedArray())
    }
    Assertions.assertEquals(1, producerCalled.get())
    sut.clear("abc")
    Assertions.assertArrayEquals(arrayOf("foo-value"), sut.get("foo").toTypedArray())
    sut.clear("foo")
    Assertions.assertArrayEquals(arrayOf("foo-value"), sut.get("foo").toTypedArray())
    Assertions.assertEquals(2, producerCalled.get())
  }

  @Test
  fun testCacheUpdateBackground(): Unit = timeoutRunBlocking {
    val sut = createSut(this)
    sut.startUpdate()
    Assertions.assertArrayEquals(arrayOf("foo-value"), sut.get("foo").toTypedArray())
    val secsToWaitForUpdate = 30
    repeat(secsToWaitForUpdate) {
      delay(1.seconds)
      if (producerCalled.get() > 5) {
        coroutineContext.job.cancelChildren()
        return@timeoutRunBlocking
      }
    }
    Assertions.fail("Even after $secsToWaitForUpdate seconds cache hasn't been upgraded in background")
  }


  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun testCacheAccessibleWhileUpdate(updateByTimer: Boolean): Unit = timeoutRunBlocking {
    val lock = CompletableDeferred<Unit>()
    val sut = Cache<String, String>(this, if (updateByTimer) 1.milliseconds else Int.MAX_VALUE.days) {
      val producerCalledTimes = producerCalled.incrementAndGet()
      var time = "first"
      if (producerCalledTimes > 1) {
        lock.await()
        time = "not-first"
      }
      val element = "$it-$time"
      listOf(element)
    }
    if (updateByTimer) {
      sut.startUpdate()
    }
    else {
      launch {
        Assertions.assertArrayEquals(arrayOf("foo-not-first"), sut.updateCache("foo").toTypedArray())
      }
    }
    repeat(20) {
      Assertions.assertArrayEquals(arrayOf("foo-first"), sut.get("foo").toTypedArray())
      delay(1.milliseconds)
    }
    lock.complete(Unit)
    val secsToWaitForUpdate = 30
    repeat(secsToWaitForUpdate) {
      delay(1.seconds)
      if (sut.get("foo").iterator().next() == "foo-not-first") {
        coroutineContext.job.cancelChildren()
        return@timeoutRunBlocking
      }
    }
    Assertions.fail("Even after $secsToWaitForUpdate seconds cache hasn't been upgraded in background")
  }
}