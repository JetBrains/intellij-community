// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.runner

import com.intellij.ide.starter.di.di
import com.intellij.tools.ide.util.common.logError
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * Ends the test as skipped. The Starter must not depend on a test framework, so the implementation comes
 * from DI, and a run finds `JUnit5TestAborter` with a `ServiceLoader`.
 */
interface TestAborter {
  fun abort(message: String, cause: Throwable): Nothing

  companion object {
    val instance: TestAborter
      get() = di.direct.instance<TestAborter>()
  }
}

/** The fallback with no test framework. It cannot skip, so it throws the cause. */
object NoTestAborter : TestAborter {
  override fun abort(message: String, cause: Throwable): Nothing {
    logError("No TestAborter is on the classpath, so the test fails instead of being skipped. $message")
    throw cause
  }
}
