// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.runner.TestAborter
import org.opentest4j.TestAbortedException

/** Skips the test. TeamCity shows it as ignored. */
class JUnit5TestAborter : TestAborter {
  override fun abort(message: String, cause: Throwable): Nothing = throw TestAbortedException(message, cause)
}
