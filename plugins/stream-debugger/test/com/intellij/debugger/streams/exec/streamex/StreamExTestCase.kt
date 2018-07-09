// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

import com.intellij.debugger.streams.exec.LibraryTraceExecutionTestCase
import com.intellij.debugger.streams.lib.LibrarySupportProvider
import com.intellij.debugger.streams.lib.impl.StreamExLibrarySupportProvider

/**
 * @author Vitaliy.Bibaev
 */
abstract class StreamExTestCase : LibraryTraceExecutionTestCase("streamex-0.6.5.jar") {
  private companion object {
    const val STREAM_EX_REFLECTION_WARNING_MESSAGE =
      "WARNING: An illegal reflective access operation has occurred\n" +
      "WARNING: Illegal reflective access by one.util.streamex.StreamExInternals (file:!LIBRARY_JAR!) to field " +
      "java.util.stream.AbstractPipeline.sourceSpliterator\n" +
      "WARNING: Please consider reporting this to the maintainers of one.util.streamex.StreamExInternals\n" +
      "WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations\n" +
      "WARNING: All illegal access operations will be denied in a future release\n"
  }

  protected abstract val packageName: String

  override fun getLibrarySupportProvider(): LibrarySupportProvider {
    return StreamExLibrarySupportProvider()
  }

  override fun replaceAdditionalInOutput(str: String): String {
    return super.replaceAdditionalInOutput(str)
      .replace("file:/!LIBRARY_JAR!", "file:!LIBRARY_JAR!")
      .replace(STREAM_EX_REFLECTION_WARNING_MESSAGE, "")
  }

  private val className: String
    get() = packageName + "." + getTestName(false)

  final override fun getTestAppRelativePath(): String = "streamex"

  protected fun doStreamExVoidTest() = doTest(true, className)
  protected fun doStreamExWithResultTest() = doTest(false, className)
}
