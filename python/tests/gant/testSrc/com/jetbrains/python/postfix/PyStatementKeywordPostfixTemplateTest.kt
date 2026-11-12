// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.postfix

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

@Subsystems.CodeCompletion
@Components.Postfix
@Layers.Functional
class PyStatementKeywordPostfixTemplateTest : PyPostfixTemplateTestCase() {

  fun testRaise() = doTest("ValueError().raise<caret>", "raise ValueError()")

  fun testYield() = doTest("value.yield<caret>", "yield value")

  override fun getTestDataDir() = ""
}
