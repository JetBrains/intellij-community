// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.java

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.platform.uast.testFramework.env.UastFixture
import com.intellij.platform.uast.testFramework.env.uastFixture
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.uast.UFile

/**
 * Returns the test data directory of the Java UAST tests: `uast/uast-tests/testData`.
 * Under Bazel, it is the runfile of this directory.
 */
fun javaUastTestDataPath(): String {
  if (BazelTestUtil.isUnderBazelTest) {
    val label = BazelLabel.fromString("@community//uast/uast-tests:testData")
    return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString()
  }
  return PathManagerEx.getCommunityHomePath() + "/uast/uast-tests/testData"
}

/**
 * Creates a [UastFixture] with the test data of [javaUastTestDataPath]. Declare it as an instance field.
 *
 * [check] is the check of [UastFixture.doTest] without a callback.
 */
fun javaUastFixture(
  projectFixture: TestFixture<LightJavaProject>,
  check: ((String, UFile) -> Unit)? = null,
): TestFixture<UastFixture> = uastFixture(projectFixture, javaUastTestDataPath(), check)
