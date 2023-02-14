// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains

import com.intellij.openapi.Disposable
import com.intellij.testFramework.TestLoggerFactory
import org.junit.rules.TestRule

/**
 * JUnit rule that captures debug logs for certain classes and reports then if test failed
 * To start it run [startLogging]
 */
class LoggingRule : TestRule by TestLoggerFactory.createTestWatcher() {
  /**
   * @param disposable logging will be stopped when [disposable] is disposed
   * @param classes classes to enable debug for
   */
  fun startLogging(disposable: Disposable, classes: Collection<Class<*>>) =
    TestLoggerFactory.enableDebugLogging(disposable, *classes.toTypedArray())
}
