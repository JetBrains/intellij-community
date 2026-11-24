// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the known decorators that the imports of a file give to a decorator name.
 */
@Subsystems.CodeInsight
@Layers.Functional
class PyKnownDecoratorsTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-94384"])
  fun `name that the imported module defines itself is not known`() {
    addFile("mod.py", """
      class CachedProperty: ...

      cached_property = CachedProperty
      """)
    val decorator = firstDecoratorOf("""
      from mod import cached_property

      @cached_property
      def f(): ...
      """)
    runReadActionBlocking {
      assertTrue(PyKnownDecoratorUtil.asKnownDecorators(decorator, stubOnlyContext()).isEmpty())
    }
  }

  @Test
  @TestFor(issues = ["PY-94384"])
  fun `name that a package re-exports by a relative import is known`() {
    addFile("pkg/__init__.py", "from ._impl import cached_property")
    addFile("pkg/_impl.py", "from functools import cached_property")
    val decorator = firstDecoratorOf("""
      from pkg import cached_property

      @cached_property
      def f(): ...
      """)
    runReadActionBlocking {
      assertEquals(listOf(PyKnownDecorator.FUNCTOOLS_CACHED_PROPERTY), PyKnownDecoratorUtil.asKnownDecorators(decorator, stubOnlyContext()))
    }
  }

  @Test
  @TestFor(issues = ["PY-94384"])
  fun `name that a package binds by an absolute and a relative import is known`() {
    addFile("pkg/__init__.py", """
      import sys

      if sys.version_info >= (3, 8):
          from functools import cached_property
      else:
          from ._backport import cached_property
      """)
    addFile("pkg/_backport.py", "class cached_property: ...")
    val decorator = firstDecoratorOf("""
      from pkg import cached_property

      @cached_property
      def f(): ...
      """)
    runReadActionBlocking {
      assertEquals(listOf(PyKnownDecorator.FUNCTOOLS_CACHED_PROPERTY), PyKnownDecoratorUtil.asKnownDecorators(decorator, stubOnlyContext()))
    }
  }

  @Test
  @TestFor(issues = ["PY-94384"])
  fun `name from a module that cannot be found matches by its short name`() {
    val decorator = firstDecoratorOf("""
      from missing import cached_property

      @cached_property
      def f(): ...
      """)
    runReadActionBlocking {
      assertTrue(PyKnownDecoratorUtil.asKnownDecorators(decorator, stubOnlyContext()).contains(PyKnownDecorator.FUNCTOOLS_CACHED_PROPERTY))
    }
  }

  private fun addFile(path: String, text: String) {
    runInEdtAndWait { myFixture.addFileToProject(path, text.trimIndent()) }
  }

  private fun firstDecoratorOf(text: String): PyDecorator {
    myFixture.configureByText("a.py", text.trimIndent())
    val file = myFixture.file as PyFile
    return runReadActionBlocking { file.findTopLevelFunction("f")!!.decoratorList!!.decorators.first() }
  }

  /**
   * The fallback context has no origin file and no stub-to-AST permission, so a decorator resolves through the imports
   * of its file.
   */
  private fun stubOnlyContext(): TypeEvalContext = TypeEvalContext.codeInsightFallback(myFixture.project)
}
