// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.testIntegration

import com.intellij.idea.TestFor
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-91546"], classes = [PyTestFinder::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyTestFinderTest : PyCodeInsightTestCase() {

  @Nested
  inner class FindSourceElement {
    @Test
    fun `source element of a caret in a class body is the class`() = withFile("""
      class Foo:
          x = 1
      #       └ CARET
    """) {
      assertEquals("Foo", findSourceName())
    }

    @Test
    fun `source element of a caret in a method is the method`() = withFile("""
      class Foo:
          def bar(self):
              pass
      #       └ CARET
    """) {
      assertEquals("bar", findSourceName())
    }

    @Test
    fun `source element of a caret in a top level function is the function`() = withFile("""
      def bar():
          pass
      #   └ CARET
    """) {
      assertEquals("bar", findSourceName())
    }

    @Test
    fun `caret outside of any class or function has no source element`() = withFile("""
      x = 1
      #   └ CARET
    """) {
      assertNull(PyTestFinder().findSourceElement(elementAtCaret()))
    }
  }

  @Nested
  inner class FindTestsForClass {
    @Test
    fun `test class is found for a production class`() = withFile("""
      import unittest

      class Foo:
      #      └ CARET
          def bar(self):
              pass

      class TestFoo(unittest.TestCase):
          def test_bar(self):
              pass
    """) {
      assertEquals(listOf("TestFoo"), findTestsForClass())
    }

    @Test
    fun `test method is found for a production method`() = withFile("""
      import unittest

      class Foo:
          def bar(self):
      #        └ CARET
              pass

      class TestFoo(unittest.TestCase):
          def test_bar(self):
              pass
    """) {
      assertEquals(listOf("test_bar"), findTestsForClass())
    }

    @Test
    fun `nothing is found for a class without tests`() = withFile("""
      class Lonely:
      #       └ CARET
          pass
    """) {
      assertEquals(emptyList<String>(), findTestsForClass())
    }

    @Test
    fun `nothing is found when there is no source element`() = withFile("""
      x = 1
      #   └ CARET
    """) {
      assertEquals(emptyList<String>(), findTestsForClass())
    }
  }

  @Nested
  inner class FindClassesForTest {
    @Test
    fun `production class is found for a test class`() = withFile("""
      import unittest

      class Foo:
          def bar(self):
              pass

      class TestFoo(unittest.TestCase):
      #          └ CARET
          def test_bar(self):
              pass
    """) {
      assertTrue(findClassesForTest().contains("Foo"), "Expected the production class among the results")
    }

    @Test
    fun `production method is found for a test method`() = withFile("""
      import unittest

      class Foo:
          def bar(self):
              pass

      class TestFoo(unittest.TestCase):
          def test_bar(self):
      #             └ CARET
              pass
    """) {
      assertTrue(findClassesForTest().contains("bar"), "Expected the production method among the results")
    }

    @Test
    fun `nothing is found for a caret outside of a class or function`() = withFile("""
      x = 1
      #   └ CARET
    """) {
      assertEquals(emptyList<String>(), findClassesForTest())
    }
  }

  @Nested
  inner class IsTest {
    @Test
    fun `element inside a test method is a test`() = withFile("""
      import unittest

      class Foo:
          def bar(self):
              pass

      class TestFoo(unittest.TestCase):
          def test_bar(self):
      #             └ CARET
              pass
    """) {
      assertTrue(PyTestFinder().isTest(elementAtCaret()))
    }

    @Test
    fun `element inside a production method is not a test`() = withFile("""
      import unittest

      class Foo:
          def bar(self):
      #        └ CARET
              pass

      class TestFoo(unittest.TestCase):
          def test_bar(self):
              pass
    """) {
      assertFalse(PyTestFinder().isTest(elementAtCaret()))
    }
  }

  private fun withFile(@Language("Python") code: String, check: () -> Unit) = runInEdtAndWait {
    configureWithCaret(code)
    check()
  }

  private fun elementAtCaret() = PsiUtilCore.getElementAtOffset(myFixture.file, myFixture.caretOffset)

  private fun findSourceName(): String? = PyTestFinder().findSourceElement(elementAtCaret())?.name

  private fun findTestsForClass(): List<String?> =
    PyTestFinder().findTestsForClass(elementAtCaret()).map { (it as PsiNamedElement).name }

  private fun findClassesForTest(): List<String?> =
    PyTestFinder().findClassesForTest(elementAtCaret()).map { (it as PsiNamedElement).name }
}
