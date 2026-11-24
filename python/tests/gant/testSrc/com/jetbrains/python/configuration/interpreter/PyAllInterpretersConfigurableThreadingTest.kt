// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.intellij.idea.TestFor
import com.intellij.openapi.util.UtilThreadingAssertions
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.ThreadingAssertions
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

@TestApplication
@TestFor(issues = ["PY-94373"])
@Subsystems.Interpreters
@Layers.Functional
class PyAllInterpretersConfigurableThreadingTest {
  private val project get() = projectFixture.get()

  /**
   * The settings dialog can create a configurable on a background thread.
   * `ThreadingAssertions` skips the AWT check in a headless application, so the test records the calls of the check itself.
   * Only some components call the check, such as `JBPanel`.
   */
  @Test
  fun `the constructor on a background thread creates no panel`(): Unit = timeoutRunBlocking {
    val awtAccesses = ConcurrentLinkedQueue<String>()
    val inConstructor = ThreadLocal.withInitial { false }
    // The static initializer of `ThreadingAssertions` installs its own hooks, so it must run before the test installs its hook.
    ThreadingAssertions.softAssertAwtOperationsThread()
    UtilThreadingAssertions.init(
      backgroundActivityAssert = { SlowOperations.assertNonCancelableSlowOperationsAreAllowed() },
      awtOperationsAssert = {
        if (inConstructor.get()) awtAccesses.add(Throwable("AWT access in the constructor").stackTraceToString())
        ThreadingAssertions.softAssertAwtOperationsThread()
      },
    )
    try {
      withContext(Dispatchers.Default) {
        inConstructor.set(true)
        try {
          PyAllInterpretersConfigurable(project)
        }
        finally {
          inConstructor.set(false)
        }
      }
    }
    finally {
      UtilThreadingAssertions.init(
        backgroundActivityAssert = { SlowOperations.assertNonCancelableSlowOperationsAreAllowed() },
        awtOperationsAssert = { ThreadingAssertions.softAssertAwtOperationsThread() },
      )
    }

    assertThat(awtAccesses).isEmpty()
  }

  companion object {
    private val projectFixture = projectFixture()
  }
}
