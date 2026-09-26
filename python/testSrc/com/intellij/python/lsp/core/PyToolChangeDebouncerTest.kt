// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.idea.TestFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The configuration of a project can set the SDK of one module after another, and each new SDK can
 * restart every server of a tool. One restart after the burst is enough.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestFor(issues = ["PY-92087"])
class PyToolChangeDebouncerTest {
  @Test
  fun `a burst runs the action one time`() = runTest {
    var runs = 0
    val debouncer = PyToolChangeDebouncer(backgroundScope, quietPeriod = 2.seconds) { runs++ }

    repeat(20) {
      debouncer.schedule()
      advanceTimeBy(1.seconds)
    }
    assertEquals(0, runs)

    advanceTimeBy(3.seconds)
    assertEquals(1, runs)
  }

  @Test
  fun `changes far apart each run the action`() = runTest {
    var runs = 0
    val debouncer = PyToolChangeDebouncer(backgroundScope, quietPeriod = 2.seconds) { runs++ }

    debouncer.schedule()
    advanceTimeBy(3.seconds)
    debouncer.schedule()
    advanceTimeBy(3.seconds)

    assertEquals(2, runs)
  }
}
