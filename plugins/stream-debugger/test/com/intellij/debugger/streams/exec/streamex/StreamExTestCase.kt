// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

import com.intellij.debugger.streams.exec.LibraryTraceExecutionTestCase
import com.intellij.debugger.streams.lib.LibrarySupportProvider
import com.intellij.debugger.streams.lib.impl.StreamExLibrarySupportProvider

/**
 * @author Vitaliy.Bibaev
 */
abstract class StreamExTestCase : LibraryTraceExecutionTestCase("streamex-0.6.5.jar") {
  abstract protected val packageName: String

  override fun getLibrarySupportProvider(): LibrarySupportProvider {
    return StreamExLibrarySupportProvider()
  }

  private val className: String
    get() = packageName + "." + getTestName(false)

  final override fun getTestAppRelativePath(): String = "streamex"

  protected fun doStreamExVoidTest() = doTest(true, className)
  protected fun doStreamExWithResultTest() = doTest(false, className)
}
