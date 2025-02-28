// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTestFixtureCompletionTest : PyTestCase() {
  override fun getTestDataPath(): String = super.getTestDataPath() + "/completion/pytestFixture"

  fun testRequestFixtureCompletion() {
    myFixture.configureByFile("test_request_fixture_completion.py")
    myFixture.completeBasic()
    myFixture.checkResultByFile("test_request_fixture_completion.after.py")
  }

  fun testRequestFixtureTypeInFixture() {
    myFixture.configureByText("test.py", """
            import pytest

            @pytest.fixture
            def my_fixture(reques<caret>t):
                pass
        """.trimIndent())

    val element = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), PyNamedParameter::class.java)
    assertNotNull("Could not find parameter element", element)
    val context = TypeEvalContext.codeAnalysis(myFixture.project, element!!.containingFile)
    val type = context.getType(element)
    assertEquals("Type should be FixtureRequest", "_pytest.fixtures.FixtureRequest", type!!.name)
  }

  fun testRequestFixtureTypeInTest() {
    myFixture.configureByText("test.py", """
            import pytest

            def test_(reques<caret>t):
                pass
        """.trimIndent())

    val element = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), PyNamedParameter::class.java)
    assertNotNull("Could not find parameter element", element)
    val context = TypeEvalContext.codeAnalysis(myFixture.project, element!!.containingFile)
    val type = context.getType(element)
    assertEquals("Type should be FixtureRequest", "_pytest.fixtures.FixtureRequest", type!!.name)
  }
}
