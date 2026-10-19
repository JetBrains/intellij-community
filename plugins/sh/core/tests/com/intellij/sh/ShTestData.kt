// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh

import com.intellij.openapi.application.PluginPathManager
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.common.BazelTestUtil

object ShTestData {
  @JvmStatic
  fun getTestDataPath(): String {
    if (BazelTestUtil.isUnderBazelTest) {
      val label = BazelLabel.fromString("@community//plugins/sh/core:testData")
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString()
    }
    return PluginPathManager.getPluginHomePath("sh") + "/core/testData"
  }
}
