// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.docstrings.PyDocStringTypeProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyKeyValueExpressionImpl
import com.jetbrains.python.psi.impl.PyNamedParameterImpl
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
import com.jetbrains.python.psi.impl.PyTypeParameterImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Type inference in a module that has no Python SDK, for example a project without an interpreter. [PyBuiltinCache]
 * has no builtins there, so `dict`, `tuple` and `typing` do not resolve.
 */
@TestFor(issues = ["PY-92874"])
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyTypesWithoutBuiltinsTest : PyCodeInsightTestCase() {
  @TestDisposable
  lateinit var testDisposable: Disposable

  @BeforeEach
  fun removePythonSdk() {
    val module = myFixture.module
    val sdk = ModuleRootManager.getInstance(module).sdk
    runInEdtAndWait { ModuleRootModificationUtil.setModuleSdk(module, null) }
    Disposer.register(testDisposable) { runInEdtAndWait { ModuleRootModificationUtil.setModuleSdk(module, sdk) } }
  }

  @Test
  @TestFor(classes = [PyNamedParameterImpl::class])
  fun `unannotated variadic parameters`() = test("""
    def f(*args, **kwargs):
        return args[0], kwargs["a"]
    #          │        └ TYPE Unknown
    #          └ TYPE Unknown
    """.trimIndent())

  @Test
  @TestFor(classes = [PyNamedParameterImpl::class])
  fun `variadic parameters of a lambda`() = test("""
    g = lambda *a, **k: (a, k)
    #                    │  └ TYPE Unknown
    #                    └ TYPE Unknown
    """.trimIndent())

  @Test
  @TestFor(classes = [PyTypingTypeProvider::class])
  fun `annotated variadic parameters`() = test("""
    class A: ...

    def f(*args: A, **kwargs: A):
        return args, kwargs
    #          │     └ TYPE Unknown
    #          └ TYPE Unknown
    """.trimIndent())

  @Test
  @TestFor(classes = [PyDocStringTypeProvider::class])
  fun `variadic parameters typed in a docstring`() = test("""
    class A: ...

    def f(*args, **kwargs):
        $tripleQuote
        :type args: A
        :type kwargs: A
        $tripleQuote
        return args, kwargs
    #          │     └ TYPE Unknown
    #          └ TYPE Unknown
    """.trimIndent())

  @Test
  @TestFor(classes = [PyTypeParameterImpl::class])
  fun `type parameter of a generic class`() = test("""
    class A: ...

    class Box[T: A]:
        t = T
    #       └ TYPE Unknown
    """.trimIndent())

  @Test
  @TestFor(classes = [PyReferenceExpressionImpl::class])
  fun `property setter`() = test("""
    class A:
        @property
        def p(self): ...

        @p.setter
    #    └ TYPE Unknown
        def p(self, value): ...
    """.trimIndent())

  @Test
  @TestFor(classes = [PyKeyValueExpressionImpl::class])
  fun `key-value pair of a dict literal`() = test("""
    expr = {"a": 1}
    #          └ TYPE Unknown
    """.trimIndent())
}
