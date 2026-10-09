// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.debugger.PySignature
import com.jetbrains.python.debugger.PySignatureCacheManager
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.ast.PyAstFunction
import org.junit.jupiter.api.Test

/**
 * The inspection compares the parameter types written in a docstring against the types the debugger recorded while
 * the code was running, so every test installs a [PySignatureCacheManager] returning a canned signature.
 */
@TestFor(issues = ["PY-91546"], classes = [PyDocstringTypesInspection::class])
@Subsystems.CodeInsight
@Layers.Functional
@PyCodeInsightTestCase.TestInspections(enableInspections = [PyDocstringTypesInspection::class])
class PyDocstringDebuggerTypesInspectionTest : PyCodeInsightTestCase() {

  // The reported range is the type inside the docstring, and a marker line can only point at the line above it,
  // so the closing quotes are pulled up onto the documented type's line.
  @Test
  fun `docstring type that contradicts the recorded one is reported`() {
    recordSignature("f", "x" to "int")
    test("""
      def f(x):
          $tripleQuote
          :param x: a value
          :type x: str$tripleQuote
      #            ^^^ WEAK-WARNING Dynamically inferred type 'int' doesn't match specified type 'str'
    """)
  }

  @Test
  fun `docstring type that agrees with the recorded one is not reported`() {
    recordSignature("f", "x" to "int")
    test("""
      def f(x):
          $tripleQuote
          :param x: a value
          :type x: int
          $tripleQuote
    """)
  }

  @Test
  fun `a docstring type wider than the recorded one is not reported`() {
    recordSignature("f", "x" to "bool")
    test("""
      def f(x):
          $tripleQuote
          :param x: a value
          :type x: int
          $tripleQuote
    """)
  }

  // A parameter seen with several types is recorded as `Union[int, str]`, a spelling PyTypeParser does not
  // understand: it resolves the bare `Union` and reports its type instead of the two recorded ones.
  @Test
  fun `a parameter recorded with several types is reported with a bogus type`() {
    recordSignature("f", "x" to "int or str")
    test("""
      from typing import Union

      def f(x):
          $tripleQuote
          :param x: a value
          :type x: bytes$tripleQuote
      #            ^^^^^ WEAK-WARNING Dynamically inferred type 'typing._SpecialForm' doesn't match specified type 'bytes' FIXME Dynamically inferred type 'int or str' doesn't match specified type 'bytes'
    """)
  }

  @Test
  fun `a parameter documented without a type is not reported`() {
    recordSignature("f", "x" to "int")
    test("""
      def f(x):
          $tripleQuote
          :param x: a value
          $tripleQuote
    """)
  }

  @Test
  fun `a parameter with no recorded type is not reported`() {
    recordSignature("f", "other" to "int")
    test("""
      def f(x):
          $tripleQuote
          :param x: a value
          :type x: str
          $tripleQuote
    """)
  }

  @Test
  fun `a function without a docstring is not reported`() {
    recordSignature("f", "x" to "int")
    test("""
      def f(x):
          pass
    """)
  }

  @Test
  fun `a plain docstring is not inspected`() {
    recordSignature("f", "x" to "int")
    test("""
      def f(x):
          ${tripleQuote}Takes an x of type str.$tripleQuote
    """)
  }

  @Test
  fun `a private function is not inspected`() {
    recordSignature("_f", "x" to "int")
    test("""
      def _f(x):
          $tripleQuote
          :param x: a value
          :type x: str
          $tripleQuote
    """)
  }

  @Test
  fun `a function without a recorded signature is not reported`() {
    test("""
      def f(x):
          $tripleQuote
          :param x: a value
          :type x: str
          $tripleQuote
    """)
  }

  @Test
  fun `the quick fix replaces the documented type with the recorded one`() {
    recordSignature("f", "x" to "int")
    testQuickFix("""
      def f(x):
          $tripleQuote
          :param x: a value
          :type x: str
          $tripleQuote
    """, "Change x type from str to int", """
      def f(x):
          $tripleQuote
          :param x: a value
          :type x: int
          $tripleQuote
    """)
  }

  @Test
  fun `the quick fix keeps the rest of the docstring intact`() {
    recordSignature("f", "x" to "int")
    testQuickFix("""
      def f(x, y):
          $tripleQuote
          Does something.

          :param x: a value
          :type x: str
          :param y: another value
          :return: nothing
          $tripleQuote
    """, "Change x type from str to int", """
      def f(x, y):
          $tripleQuote
          Does something.

          :param x: a value
          :type x: int
          :param y: another value
          :return: nothing
          $tripleQuote
    """)
  }

  @TestDisposable
  private lateinit var serviceDisposable: Disposable

  /** Makes the signature cache report [args] as the runtime types of [functionName], as the debugger would. */
  private fun recordSignature(functionName: String, vararg args: Pair<String, String>) {
    val signature = PySignature("aaa.py", functionName)
    for ((name, type) in args) {
      signature.addArgument(name, type)
    }
    val manager = StubSignatureCacheManager(signature)
    myFixture.project.replaceService(PySignatureCacheManager::class.java, manager, serviceDisposable)
  }

  private class StubSignatureCacheManager(private val signature: PySignature) : PySignatureCacheManager() {
    override fun recordSignature(signature: PySignature) {}

    override fun findParameterType(function: PyAstFunction, name: String): String? =
      findSignature(function)?.getArgTypeQualifiedName(name)

    override fun findSignature(function: PyAstFunction): PySignature? =
      signature.takeIf { it.functionName == function.name }

    override fun clearCache(): Boolean = true
  }
}
