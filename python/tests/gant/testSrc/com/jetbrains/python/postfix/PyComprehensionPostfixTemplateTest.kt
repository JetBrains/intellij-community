// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.postfix

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

@Subsystems.CodeCompletion
@Components.Postfix
@Layers.Functional
class PyComprehensionPostfixTemplateTest : PyPostfixTemplateTestCase() {

  fun testList() = doTest("[1, 2, 3].compl<caret>", "[e for e in [1, 2, 3]]\n")

  fun testSet() = doTest("[1, 2, 3].comps<caret>", "{e for e in [1, 2, 3]}\n")

  fun testGenerator() = doTest("[1, 2, 3].compg<caret>", "(e for e in [1, 2, 3])\n")

  fun testDict() = doTest("[1, 2, 3].compd<caret>", "{e: e for e in [1, 2, 3]}\n")

  override fun getTestDataDir() = ""
}
