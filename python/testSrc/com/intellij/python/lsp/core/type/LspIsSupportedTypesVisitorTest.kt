// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.type

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCapturePattern
import com.jetbrains.python.psi.PyClassPattern
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyLiteralPattern
import com.jetbrains.python.psi.PyMappingPattern
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequencePattern
import com.jetbrains.python.psi.PySetCompExpression
import com.jetbrains.python.psi.PySetLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyWildcardPattern
import com.jetbrains.python.psi.PyYieldExpression
import kotlin.reflect.KClass

class LspIsSupportedTypesVisitorTest : PyTestCase() {

  private fun <T : PsiElement> testElementSupported(elementClass: KClass<T>, code: String) {
    myFixture.configureByText("test.py", code.trimIndent())
    
    val element = PsiTreeUtil.findChildOfType(myFixture.file, elementClass.java)
    assertNotNull("Should find ${elementClass.simpleName} in file", element)
    
    val visitor = LspIsSupportedTypesVisitor()
    element!!.accept(visitor)
    assertTrue("Should detect ${elementClass.simpleName} as supported", visitor.isSupported)
  }

  fun `test PyMappingPattern is supported`() = testElementSupported(PyMappingPattern::class, """
    def match_example(value):
        match value:
            case {"key": val, **other}:
                return f"mapping: key={val}, other={other}"
  """)

  fun `test PySequencePattern is supported`() = testElementSupported(PySequencePattern::class, """
    def match_example(value):
        match value:
            case [first, *rest]:
                return f"sequence: first={first}, rest={rest}"
  """)

  fun `test PyBinaryExpression is supported`() = testElementSupported(PyBinaryExpression::class, "x = y + z")

  fun `test PyCallExpression is supported`() = testElementSupported(PyCallExpression::class, "result = some_function(1, 2, 3)")

  fun `test PyCapturePattern is supported`() = testElementSupported(PyCapturePattern::class, """
    def match_example(value):
        match value:
            case x:
                return f"captured {x}"
  """)

  fun `test PyClassPattern is supported`() = testElementSupported(PyClassPattern::class, """
    def match_example(value):
        match value:
            case dict() as d:
                return f"class pattern: {d}"
  """)

  fun `test PyDecorator is supported`() = testElementSupported(PyDecorator::class, """
    @property
    def test_function():
        pass
  """)

  fun `test PyDictLiteralExpression is supported`() = testElementSupported(PyDictLiteralExpression::class, """dict_val = {"key": "value", "num": 42}""")

  fun `test PyFunction is supported`() = testElementSupported(PyFunction::class, """
    def test_function():
        pass
  """)

  fun `test PyLambdaExpression is supported`() = testElementSupported(PyLambdaExpression::class, "lambda_func = lambda a, b: a + b")

  fun `test PyListLiteralExpression is supported`() = testElementSupported(PyListLiteralExpression::class, "list_val = [1, 2, 3]")

  fun `test PyLiteralPattern is supported`() = testElementSupported(PyLiteralPattern::class, """
    def match_example(value):
        match value:
            case 42:
                return "literal"
  """)

  fun `test PyNamedParameter is supported`() = testElementSupported(PyNamedParameter::class, """
    def test_function(param: int):
        pass
  """)

  fun `test PyPrefixExpression is supported`() = testElementSupported(PyPrefixExpression::class, "negative = -x")

  fun `test PyReferenceExpression is supported`() = testElementSupported(PyReferenceExpression::class, "x = y")

  fun `test PySetCompExpression is supported`() = testElementSupported(PySetCompExpression::class, "set_comp = {x for x in range(10) if x % 2 == 0}")

  fun `test PySetLiteralExpression is supported`() = testElementSupported(PySetLiteralExpression::class, "set_val = {1, 2, 3}")

  fun `test PySubscriptionExpression is supported`() = testElementSupported(PySubscriptionExpression::class, "indexed = list_val[0]")

  fun `test PyTargetExpression is supported`() = testElementSupported(PyTargetExpression::class, "x = 42")

  fun `test PyWildcardPattern is supported`() = testElementSupported(PyWildcardPattern::class, """
    def match_example(value):
        match value:
            case _:
                return "wildcard"
  """)

  fun `test PyYieldExpression is supported`() = testElementSupported(PyYieldExpression::class, """
    def generator():
        yield 42
  """)

  fun `test unsupported file returns false`() {
    myFixture.configureByText("empty.py", "# Just a comment")

    val visitor = LspIsSupportedTypesVisitor()
    myFixture.file.accept(visitor)
    assertFalse("Should not detect supported types in comment-only file", visitor.isSupported)
  }
}