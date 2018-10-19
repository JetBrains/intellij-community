// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  fun startLogging(disposable: Disposable, classes: Iterable<Class<*>>) {
    TestLoggerFactory.enableDebugLogging(disposable, *classes.map { "#" + it.`package`.name }.toTypedArray())
  }
}