// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.postfix

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

@Subsystems.CodeCompletion
@Components.Postfix
@Layers.Functional
class PyIsInstancePostfixTemplateTest : PyPostfixTemplateTestCase() {

  fun testSimple() = doTest()

  fun testAsExpr() = doTest()

  fun testQualified() = doTest()

  override fun getTestDataDir() = "isinstance/"
}
