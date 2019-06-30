// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.openapi.application.ex.PathManagerEx
import org.jetbrains.uast.test.env.AbstractUastFixtureTest
import java.io.File

abstract class AbstractJavaUastTest : AbstractUastFixtureTest() {
  protected companion object {
    val TEST_JAVA_MODEL_DIR = File(PathManagerEx.getCommunityHomePath(), "uast/uast-tests/java")
  }

  override fun getTestFile(testName: String): File = File(TEST_JAVA_MODEL_DIR, testName)
}