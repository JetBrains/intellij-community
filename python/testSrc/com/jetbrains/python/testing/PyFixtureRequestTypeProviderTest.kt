// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.PyFixtureRequestType

class PyFixtureRequestTypeProviderTest : PyTestCase() {
  override fun setUp() {
    super.setUp()
    // Ensure pytest is the selected runner so isPyTestEnabled(module) returns true
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
  }

  private fun getTypeAtCaret(fileName: String, text: String): PyType? {
    myFixture.configureByText(fileName, text)
    val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
    val param = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java) ?: return null
    val context = TypeEvalContext.codeInsightFallback(myFixture.project)
    return context.getType(param)
  }

  private fun assertTopRequest(type: PyType?) {
    assertNotNull("Expected non-null type", type)
    assertTrue("Expected PyFixtureRequestType.TopRequest", type is PyFixtureRequestType.TopRequest)
  }

  private fun assertSubRequest(type: PyType?) {
    assertNotNull("Expected non-null type", type)
    assertTrue("Expected PyFixtureRequestType.SubRequest", type is PyFixtureRequestType.SubRequest)
  }

  fun testDoesNotApplyInNonPytestCode() {
    val type = getTypeAtCaret(
      "views.py",
      """
      def index(<caret>request):
          x = request.POST.getlist("foo")
          return x
      """.trimIndent()
    )
    assertNull(type)
  }

  fun testTopRequestInTestFunction() {
    val type = getTypeAtCaret(
      "test_sample.py",
      """
      def test_foo(<caret>request):
          assert request is not None
      """.trimIndent()
    )
    assertTopRequest(type)
  }

  fun testSubRequestInFixtureFunction() {
    val type = getTypeAtCaret(
      "conftest.py",
      """
      import pytest
      @pytest.fixture
      def myfix(<caret>request):
          return request
      """.trimIndent()
    )
    assertSubRequest(type)
  }

  fun testTopRequestInClassTestMethod() {
    val type = getTypeAtCaret(
      "test_class.py",
      """
      class TestC:
          def test_bar(self, <caret>request):
              assert request is not None
      """.trimIndent()
    )
    assertTopRequest(type)
  }
}
