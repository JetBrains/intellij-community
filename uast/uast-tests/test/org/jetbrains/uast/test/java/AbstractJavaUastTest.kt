// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.platform.uast.testFramework.env.AbstractUastFixtureTest
import com.intellij.testFramework.common.BazelTestUtil

abstract class AbstractJavaUastTest : AbstractUastFixtureTest() {
  public override fun getTestDataPath(): String {
    if (BazelTestUtil.isUnderBazelTest) {
      val label = BazelLabel.fromString("@community//uast/uast-tests:java")
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString()
    }
    return PathManagerEx.getCommunityHomePath() + "/uast/uast-tests/java"
  }
}
