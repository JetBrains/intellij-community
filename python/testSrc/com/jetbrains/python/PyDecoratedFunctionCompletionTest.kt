// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.idea.TestFor
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.resolve.CompletionVariantsProcessor
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-51321"], classes = [CompletionVariantsProcessor::class])
@Subsystems.CodeCompletion
@Layers.Functional
class PyDecoratedFunctionCompletionTest : PyCodeInsightTestCase() {

  @Test
  fun `class decorator in a from import`() {
    val item = complete("foo", module = """
      class A:
          def __init__(self, fn): ...

      @A
      def foo(): ...
    """, text = """
      from mod import X
      #               └ CARET
    """)
    assertEquals(VARIABLE, item.icon)
    assertNull(item.tailText)
  }

  @Test
  fun `decorator that returns an int in a from import`() {
    val item = complete("foo", module = """
      def bar(fn: object) -> int: ...

      @bar
      def foo(): ...
    """, text = """
      from mod import X
      #               └ CARET
    """)
    assertEquals(VARIABLE, item.icon)
  }

  @Test
  fun `class decorator in a module attribute reference`() {
    val item = complete("foo", module = """
      class A:
          def __init__(self, fn): ...

      @A
      def foo(x): ...
    """, text = """
      import mod
      mod.X
      #   └ CARET
    """)
    assertEquals(VARIABLE, item.icon)
    assertNull(item.tailText)
  }

  @Test
  fun `class decorator in a reference in the same file`() {
    val item = complete("foo", text = """
      class A:
          def __init__(self, fn): ...

      @A
      def foo(x): ...

      X
      #\ CARET
    """)
    assertEquals(VARIABLE, item.icon)
    assertNull(item.tailText)
  }

  @Test
  fun `decorator that returns a callable keeps the function presentation`() {
    val item = complete("foo", module = """
      from typing import Callable

      def bar(fn: object) -> Callable[[int], str]: ...

      @bar
      def foo(): ...
    """, text = """
      from mod import X
      #               └ CARET
    """)
    assertEquals(FUNCTION, item.icon)
  }

  @Test
  fun `decorator class with a call method keeps the function presentation`() {
    val item = complete("foo", module = """
      class A:
          def __init__(self, fn): ...
          def __call__(self): ...

      @A
      def foo(): ...
    """, text = """
      from mod import X
      #               └ CARET
    """)
    assertEquals(FUNCTION, item.icon)
  }

  @Test
  fun `untyped decorator keeps the function presentation`() {
    val item = complete("foo", module = """
      def bar(fn): ...

      @bar
      def foo(): ...
    """, text = """
      from mod import X
      #               └ CARET
    """)
    assertEquals(FUNCTION, item.icon)
  }

  @Test
  fun `functools cache keeps the function presentation`() {
    val item = complete("foo", module = """
      import functools

      @functools.cache
      def foo(): ...
    """, text = """
      from mod import X
      #               └ CARET
    """)
    assertEquals(FUNCTION, item.icon)
  }

  @Test
  fun `staticmethod keeps the method presentation`() {
    val item = complete("foo", text = """
      class C:
          @staticmethod
          def foo(x): ...

      C.X
      # └ CARET
    """)
    assertEquals(METHOD, item.icon)
    assertEquals("(x)", item.tailText)
  }

  @Test
  fun `singledispatchmethod keeps the method presentation`() {
    val item = complete("foo", text = """
      from functools import singledispatchmethod

      class C:
          @singledispatchmethod
          def foo(self, x): ...

      C().X
      #   └ CARET
    """)
    assertEquals(METHOD, item.icon)
  }

  @Test
  fun `descriptor alias of classmethod keeps the method presentation`() {
    val item = complete("foo", text = """
      my_cm = classmethod

      class C:
          @my_cm
          def foo(cls, x): ...

      C().X
      #   └ CARET
    """)
    assertEquals(METHOD, item.icon)
  }

  @Test
  fun `class decorator on a method`() {
    val item = complete("foo", text = """
      class A:
          def __init__(self, fn): ...

      class C:
          @A
          def foo(self): ...

      C().X
      #   └ CARET
    """)
    assertEquals(VARIABLE, item.icon)
    assertNull(item.tailText)
  }

  @Test
  fun `completion does not load the tree of the imported module`() {
    val item = complete("foo", module = """
      class A:
          def __init__(self, fn): ...

      @A
      def foo(): ...
    """, text = """
      from mod import X
      #               └ CARET
    """) { module ->
      assertNull((module as PsiFileImpl).treeElement, "The tree of mod.py is loaded")
    }
    assertEquals(VARIABLE, item.icon)
  }

  private fun complete(
    name: String,
    @Language("Python") module: String? = null,
    @Language("Python") text: String,
    checkModule: (PsiFile) -> Unit = {},
  ): LookupElementPresentation = runInEdtAndGet {
    val moduleFile = module?.let { myFixture.addFileToProject("mod.py", it.trimIndent()) }
    configureWithCaret(text)
    myFixture.completeBasic()
    val elements = myFixture.lookupElements.orEmpty()
    val element = elements.singleOrNull { it.lookupString == name } ?: error("$name is not in ${elements.map { it.lookupString }}")
    val presentation = LookupElementPresentation.renderElement(element)
    moduleFile?.let(checkModule)
    presentation
  }

  private companion object {
    val VARIABLE = IconManager.getInstance().getPlatformIcon(PlatformIcons.Variable)
    val FUNCTION = IconManager.getInstance().getPlatformIcon(PlatformIcons.Function)
    val METHOD = IconManager.getInstance().getPlatformIcon(PlatformIcons.Method)
  }
}
