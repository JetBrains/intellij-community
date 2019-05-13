// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.test.DslTestCase
import com.intellij.debugger.streams.trace.dsl.impl.DslImpl
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory
import com.intellij.openapi.application.PluginPathManager

/**
 * @author Vitaliy.Bibaev
 */
class JavaDslTest : DslTestCase(DslImpl(JavaStatementFactory())) {
  override fun getTestDataPath(): String {
    return PluginPathManager.getPluginHomePath("stream-debugger") + "/testData/dsl/java"
  }
}