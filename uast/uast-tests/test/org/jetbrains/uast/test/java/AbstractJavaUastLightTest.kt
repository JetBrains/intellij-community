// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class AbstractJavaUastLightTest : LightJavaCodeInsightFixtureTestCase() {
  protected fun fail(message: String): Nothing {
    throw AssertionError(message)
  }
}