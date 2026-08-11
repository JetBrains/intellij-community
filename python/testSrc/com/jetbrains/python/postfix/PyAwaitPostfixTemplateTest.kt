// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.postfix

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.postfix.PyAwaitPostfixTemplate

@Subsystems.CodeCompletion
@Components.Postfix
@Layers.Functional
@TestFor(issues = ["PY-84226"], classes = [PyAwaitPostfixTemplate::class])
class PyAwaitPostfixTemplateTest : PyPostfixTemplateTestCase() {

  fun testSimple() = doTest("async def f():\n    foo().await<caret>", "async def f():\n    await foo()")

  fun testAsExpr() = doTest("async def f():\n    x = foo().await<caret>", "async def f():\n    x = await foo()")

  // "await" is only valid inside an async context, so the postfix must not expand elsewhere.
  fun testNotApplicableInSyncFunction() = assertNotExpanded("def f():\n    foo().await<caret>")

  fun testNotApplicableAtModuleLevel() = assertNotExpanded("foo().await<caret>")

  private fun assertNotExpanded(input: String) {
    myFixture.configureByText("input.py", input)
    myFixture.type("\t")
    val text = myFixture.file.text
    assertTrue(text, text.contains("foo().await"))
  }

  override fun getTestDataDir() = ""
}
