// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.streamex

import com.intellij.debugger.streams.lib.impl.StreamExLibrarySupportProvider
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper
import com.intellij.debugger.streams.test.TraceExecutionFixture
import com.intellij.debugger.streams.test.TraceHelperFactory
import com.intellij.debugger.streams.test.traceExecutionFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.PathUtil
import one.util.streamex.StreamEx

/**
 * Creates the fixture of the StreamEx trace tests: a [traceExecutionFixture] on the `streamex` test app with the StreamEx library.
 * The main class of a test is the class with the name of the test in the package [packageName].
 * Declare it as an instance field of a `@TestApplication` class.
 */
fun streamExFixture(
  packageName: String,
  helper: TraceHelperFactory = TraceHelperFactory(::ExecutionTestCaseHelper),
): TestFixture<StreamExFixture> = testFixture("streamExFixture") {
  val trace = traceExecutionFixture(
    helper = helper,
    replaceAdditionalInOutput = { it.replace("file:/!LIBRARY_JAR!", "file:!LIBRARY_JAR!") },
    testAppRelativePath = "streamex",
    librarySupportProvider = StreamExLibrarySupportProvider(),
    libraryJarPath = PathUtil.getJarPathForClass(StreamEx::class.java),
  ).init()
  initialized(StreamExFixture(trace, packageName)) {}
}

/** The state and the helpers of a test that [streamExFixture] creates. */
class StreamExFixture(val trace: TraceExecutionFixture, private val packageName: String) {
  private val className: String
    get() = packageName + "." + trace.testName

  /** Runs the test of the main class whose stream has no result. */
  fun doStreamExVoidTest() {
    trace.doTest(true, className)
  }

  /** Runs the test of the main class whose stream has a result. */
  fun doStreamExWithResultTest() {
    trace.doTest(false, className)
  }
}
