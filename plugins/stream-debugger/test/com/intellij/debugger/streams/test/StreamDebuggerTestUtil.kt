// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.test

import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.common.BazelTestUtil

object StreamDebuggerTestUtil {
  @JvmStatic
  fun getTestDataPath(relativePath: String): String {
    val root = if (BazelTestUtil.isUnderBazelTest) {
      BazelTestUtil.getFileFromBazelRuntime("@community//plugins/stream-debugger:testData")
    }
    else {
      PluginPathManager.getPluginHome("stream-debugger").toPath().resolve("testData")
    }
    return root.resolve(relativePath).toAbsolutePath().toString()
  }
}
