// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * `A | B` in a type hint is the PEP 604 union form, not an `__or__` call, so the type checker must not check
 * it as a call. Outside a type hint the same syntax IS a call and keeps its check (PY-91327).
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(issues = ["PY-91327"])
class PyPep604AnnotationTest : PyCodeInsightTestCase() {

  @ParameterizedTest
  @CsvSource("+, __add__", "|, __or__")
  fun `operators in annotated metadata are checked`(operator: String, method: String) = test("""
    from typing import Annotated
    class Metadata:
        def $method(self, other: int) -> int: ...
    def f(value: str) -> None:
        x: Annotated[int, Metadata() $operator value]  # WARNING Expected type 'int', got 'str' instead
    """.trimIndent())

  @Test
  fun `a union in the type argument of annotated is accepted`() = test("""
    from typing import Annotated as Hint
    class A: pass
    class B: pass
    def f(x: Hint[A | B, "metadata"]) -> None: ...
    """.trimIndent())

  @Test
  fun `a pep604 union of classes without __or__ is not reported`() = test("""
    class A: pass
    class B: pass
    def f(u: A | B) -> None:
        pass
    """.trimIndent())

  @Test
  fun `a pep604 union is not reported in any hint position`() = test("""
    class A: pass
    class B: pass
    class C: pass

    def f(u: A | B) -> A | B: ...
    x: A | B | C
    def g(v: list[A | B]) -> None: ...

    class Holder:
        field: A | B

    def h() -> None:
        local: A | B = A()
    """.trimIndent())

  /** A type comment is a hint too, so the same rule applies there. */
  @Test
  fun `a pep604 union in a type comment is not reported`() = test("""
    class A: pass
    class B: pass
    def f():
        x = A()  # type: A | B
    """.trimIndent())

  /** The guard must not reach a real `|`. These classes define no `__or__`, so the operator is an error. */
  @Test
  fun `an or operator outside a type hint is still reported`() = test("""
    class A: pass
    class B: pass
    def f(a: A, b: B) -> None:
        c = a | b  # WARNING Class 'A' does not define '__or__', so the '|' operator cannot be used on its instances
    """.trimIndent())

  /** An explicit type alias is a hint, but the assignment's own value is not a union of types to check. */
  @Test
  fun `a pep604 union in an explicit type alias is not reported`() = test("""
    from typing import TypeAlias
    class A: pass
    class B: pass
    AB: TypeAlias = A | B
    def f(u: AB) -> None: ...
    """.trimIndent())
}
