// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.uast.testFramework.env.AbstractUastFixtureTest

abstract class AbstractJavaUastTest : AbstractUastFixtureTest() {
  public override fun getTestDataPath(): String =
    PathManagerEx.getCommunityHomePath() + "/uast/uast-tests/java"
}