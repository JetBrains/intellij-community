// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.docstrings.PyDocStringTypeProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.fixtures.findPythonInterpreterForTests
import com.jetbrains.python.fixtures.setPythonInterpreterForTests
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyKeyValueExpressionImpl
import com.jetbrains.python.psi.impl.PyMappingPatternImpl
import com.jetbrains.python.psi.impl.PyNamedParameterImpl
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
import com.jetbrains.python.psi.impl.PySequencePatternImpl
import com.jetbrains.python.psi.impl.PyTypeAliasStatementImpl
import com.jetbrains.python.psi.impl.PyTypeParameterImpl
import com.jetbrains.python.psi.types.PyLegacyDocstringTypeParser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Type inference in a module that has no interpreter, for example a project without one. [PyBuiltinCache]
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
  fun removePythonInterpreter() {
    val module = myFixture.module
    val interpreter = findPythonInterpreterForTests(module)
    setPythonInterpreterForTests(module, null)
    Disposer.register(testDisposable) { setPythonInterpreterForTests(module, interpreter) }
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
        '''
        :type args: A
        :type kwargs: A
        '''
        return args, kwargs
    #          │     └ TYPE Unknown
    #          └ TYPE Unknown
    """.trimIndent())

  @Test
  @TestFor(classes = [PyLegacyDocstringTypeParser::class])
  fun `numeric tower types in a docstring`() = test("""
    def f(x, y):
        '''
        :type x: float
        :type y: complex
        '''
        return x, y
    #          │  └ TYPE Unknown
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

  @Test
  @TestFor(classes = [PyMappingPatternImpl::class])
  fun `empty mapping pattern`() = test("""
    def f(x):
        match x:
            case {}:
    #            └ TYPE Unknown
                pass
    """.trimIndent())

  @Test
  @TestFor(classes = [PySequencePatternImpl::class])
  fun `sequence pattern`() = test("""
    def f(x):
        match x:
            case [a]:
    #            └ TYPE Unknown
                pass
    """.trimIndent())

  @Test
  @TestFor(classes = [PyTypingTypeProvider::class])
  fun `type hints that need the str and type builtins`() = test("""
    from typing import Any, LiteralString, Type

    def f(x: Type[Any], y: Type, z: LiteralString):
        return x, y, z
    #          │  │  └ TYPE Unknown
    #          │  └ TYPE Unknown
    #          └ TYPE Unknown
    """.trimIndent(),
    "typing.py" to """
    Any = object()
    LiteralString = object()
    Type = object()
    """.trimIndent(),
  )

  @Test
  @TestFor(classes = [PyStdlibTypeProvider::class])
  fun `value of a StrEnum`() = test("""
    from enum import StrEnum

    def f(x: StrEnum):
        expr = x.value
    #   └ TYPE Unknown
    """.trimIndent(),
    "enum.py" to """
    class EnumMeta(type): ...

    class Enum(metaclass=EnumMeta):
        def value(self): ...

    class StrEnum(str, Enum):
        def value(self): ...
    """.trimIndent(),
  )

  @Test
  @TestFor(classes = [PyTypeAliasStatementImpl::class])
  fun `type alias statement`() = test("""
    type Alias = int
    expr = Alias
    #      └ TYPE Unknown
    """.trimIndent())
}
