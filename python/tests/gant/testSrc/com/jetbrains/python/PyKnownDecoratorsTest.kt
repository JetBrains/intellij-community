// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.decorator.PyDecoratedFunctionTypeProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyElementKnownDecorators
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyKnownDecoratorProvider
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyKnownDecorators
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests the known-decorator aggregate itself, independent of any library.
 *
 * The candidates of one decorator are the known decorators it *may* resolve to, so every predicate
 * over them folds with "any". A fake provider supplies two candidates that share a short name, the shape the
 * stub-only resolve path produces for a real import.
 */
@Subsystems.CodeInsight
@Layers.Functional
class PyKnownDecoratorsTest : PyCodeInsightTestCase() {

  @TestDisposable
  lateinit var testDisposable: Disposable

  @BeforeEach
  fun registerFakeProvider() {
    val ep = PyKnownDecoratorProvider.EP_NAME
    ExtensionTestUtil.maskExtensions(ep, ep.extensionList + FakeProvider(), testDisposable)
  }

  @Test
  @TestFor(issues = ["PY-93188"], classes = [PyKnownDecorators::class])
  fun `trait on one of several candidates holds`() {
    val decorator = firstDecoratorOf("""
      from faketyping import fakefinal

      @fakefinal
      def f(): ...
      """)
    runReadActionBlocking {
      val candidates = PyKnownDecoratorUtil.knownDecoratorsOf(decorator, stubOnlyContext())

      assertTrue(candidates.contains(FAKE_FINAL_ABSTRACT), "The resolve is ambiguous, so both candidates are expected")
      assertTrue(candidates.contains(FAKE_FINAL_PLAIN))
      assertTrue(candidates.hasTrait(PyKnownDecorator::isAbstract), "A trait holds when it holds for any candidate, not for all of them")
    }
  }

  @Test
  @TestFor(issues = ["PY-93188"], classes = [PyKnownDecorators::class])
  fun `decorator without qualified name is not known`() {
    val decorator = firstDecoratorOf("""
      @(lambda fn: fn)
      def f(): ...
      """)
    runReadActionBlocking {
      val candidates = PyKnownDecoratorUtil.knownDecoratorsOf(decorator, stubOnlyContext())

      assertFalse(candidates.isKnown())
      assertFalse(candidates.hasTrait(PyKnownDecorator::isAbstract))
    }
  }

  // Resolving a property accessor would recurse back into property resolution, so these are never known.
  // FAKE_SETTER makes the short name `setter` known, so only the accessor guard can keep this decorator unknown.
  @Test
  @TestFor(issues = ["PY-93188"], classes = [PyKnownDecorators::class])
  fun `property accessor decorator is not known`() {
    val file = configure("""
      class C:
          @property
          def x(self): ...

          @x.setter
          def x(self, value): ...
      """)
    runReadActionBlocking {
      val setter = PsiTreeUtil.findChildrenOfType(file, PyDecorator::class.java)
        .single { it.qualifiedName.toString() == "x.setter" }

      assertFalse(PyKnownDecoratorUtil.knownDecoratorsOf(setter, stubOnlyContext()).isKnown())
    }
  }

  @Test
  @TestFor(issues = ["PY-93188"], classes = [PyElementKnownDecorators::class])
  fun `element without decorators has all decorators known`() {
    val function = functionOf("""
      def f(): ...
      """)
    runReadActionBlocking {
      val decorators = PyKnownDecoratorUtil.knownDecoratorsOf(function, stubOnlyContext())

      assertTrue(decorators.allKnown(), "An element with no decorator has no unknown one")
      assertFalse(PyKnownDecoratorUtil.hasUnknownDecorator(function, stubOnlyContext()))
    }
  }

  @Test
  @TestFor(issues = ["PY-93188"], classes = [PyElementKnownDecorators::class])
  fun `unknown decorator makes not all known`() {
    val function = functionOf("""
      from faketyping import fakefinal

      @fakefinal
      @totally_unknown
      def f(): ...
      """)
    runReadActionBlocking {
      val decorators = PyKnownDecoratorUtil.knownDecoratorsOf(function, stubOnlyContext())

      assertFalse(decorators.allKnown())
      assertTrue(decorators.contains(FAKE_FINAL_ABSTRACT), "A known decorator is still found among the others")
    }
  }

  // The size comparison this replaced collapsed two decorators of one short name into a single group
  // and wrongly reported an unknown decorator.
  @Test
  @TestFor(issues = ["PY-93188"], classes = [PyElementKnownDecorators::class])
  fun `two decorators sharing short name are all known`() {
    val function = functionOf("""
      from faketyping import fakefinal

      @fakefinal
      @fakefinal
      def f(): ...
      """)
    runReadActionBlocking {
      val decorators = PyKnownDecoratorUtil.knownDecoratorsOf(function, stubOnlyContext())

      assertTrue(decorators.allKnown(), "Both decorators resolve to a known decorator")
      assertFalse(PyKnownDecoratorUtil.hasUnknownDecorator(function, stubOnlyContext()))
    }
  }

  // The decorator nearest the function decides, so @classmethod wins over the @staticmethod wrapping it.
  @Test
  @TestFor(issues = ["PY-93188"], classes = [PyElementKnownDecorators::class])
  fun `nearest decorator with trait wins`() {
    val function = functionOf("""
      @staticmethod
      @classmethod
      def f(): ...
      """)
    runReadActionBlocking {
      val decorators = PyKnownDecoratorUtil.knownDecoratorsOf(function, stubOnlyContext())

      val nearest = decorators.findLastWithTrait { it.isClassMethod || it.isStaticMethod }
      assertEquals(PyKnownDecorator.CLASSMETHOD, nearest)
    }
  }

  // `FAKE_SKIPPED_WRAP` is registered as `a.skippedwrap`, so the decorator has to live in `a.py`.
  @Test
  @TestFor(classes = [PyDecoratedFunctionTypeProvider::class])
  @PyCodeInsightTestCase.TestCaseOptions(testFileName = "a.py")
  fun `known decorator skips typed call by default`() = test("""
    from typing import Callable

    def skippedwrap(fn) -> Callable[[], int]: ...

    @skippedwrap
    def g() -> str: ...

    expr = g()
    #└ TYPE str
    """.trimIndent())

  private fun configure(text: String): PyFile {
    myFixture.configureByText("a.py", text.trimIndent())
    return myFixture.file as PyFile
  }

  /** Configures [text] and returns its function `f`. */
  private fun functionOf(text: String): PyFunction {
    val file = configure(text)
    return runReadActionBlocking { file.findTopLevelFunction("f")!! }
  }

  private fun firstDecoratorOf(text: String): PyDecorator {
    val function = functionOf(text)
    return runReadActionBlocking { function.decoratorList!!.decorators.first() }
  }

  /**
   * The fallback context is built with no origin file and no stub-to-AST permission, so `maySwitchToAST`
   * is false for every element — exactly the path that resolves a decorator through the imports of its file
   * and can yield several candidates.
   */
  private fun stubOnlyContext(): TypeEvalContext = TypeEvalContext.codeInsightFallback(myFixture.project)

  class FakeProvider : PyKnownDecoratorProvider {
    override fun getKnownDecorators(): Collection<PyKnownDecorator> =
      listOf(FAKE_FINAL_ABSTRACT, FAKE_FINAL_PLAIN, FAKE_SETTER, FAKE_SKIPPED_WRAP)
  }

  private companion object {
    // Two decorators with one short name and a shared first component: `from faketyping import fakefinal`
    // matches neither fully qualified name, so the short-name fallback yields both as candidates.
    val FAKE_FINAL_ABSTRACT: PyKnownDecorator = PyKnownDecorator.Builder("faketyping.a.fakefinal").setAbstract().build()
    val FAKE_FINAL_PLAIN: PyKnownDecorator = PyKnownDecorator.Builder("faketyping.b.fakefinal").build()

    // Matches the short name of a property accessor, so only the accessor guard keeps `@x.setter` from being known.
    val FAKE_SETTER: PyKnownDecorator = PyKnownDecorator.Builder("faketyping.setter").build()

    val FAKE_SKIPPED_WRAP: PyKnownDecorator = PyKnownDecorator.Builder("a.skippedwrap").build()
  }
}
