// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

/**
 * The override check compares the whole overload group with the base method when one of the two methods is overloaded.
 */
@TestFor(classes = [PyMethodOverridingInspection::class], issues = ["PY-88976"])
@Subsystems.Inspections
@Layers.Functional
class PyOverloadedMethodOverrideTest : PyCodeInsightTestCase() {

  @Test
  fun `overload group with the same overloads`() = test("""
    from typing import overload

    class A:
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x

    class B(A):
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x
    """)

  @Test
  fun `overload group with an incompatible overload`() = test("""
    from typing import overload

    class A:
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x

    class B(A):
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: None) -> str: ...
        def foo(self, x: int | None) -> int | str: # WARNING Signature of method 'B.foo()' does not match signature of the base method in class 'A'
            return 0
    """)

  @Test
  fun `overload group with a missing overload`() = test("""
    from typing import overload

    class A:
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x

    class B(A):
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: bytes) -> bytes: ...
        def foo(self, x: int | bytes) -> int | bytes: # WARNING Signature of method 'B.foo()' does not match signature of the base method in class 'A'
            return x
    """)

  @Test
  fun `overload group with an additional overload`() = test("""
    from typing import overload

    class A:
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x

    class B(A):
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        @overload
        def foo(self, x: bytes) -> bytes: ...
        def foo(self, x: int | str | bytes) -> int | str | bytes:
            return x
    """)

  @Test
  fun `single method with a declared return type that does not match the overloads of the base method`() = test("""
    from typing import overload

    class A:
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x

    class B(A):
        def foo(self, x: object) -> None: # WARNING Signature of method 'B.foo()' does not match signature of the base method in class 'A'
            return None
    """)

  // The check compares the return types only when both methods declare them, as for a method with no overloads (PY-87372).
  @Test
  fun `single method with no return annotation over the overloads of the base method`() = test("""
    from typing import overload

    class A:
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x

    class B(A):
        def foo(self, x: object):
            return None
    """)

  @Test
  fun `single method with incompatible parameters over the overloads of the base method`() = test("""
    from typing import overload

    class A:
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x

    class B(A):
        def foo(self, x: int): # WARNING Signature of method 'B.foo()' does not match signature of the base method in class 'A'
            return None
    """)

  @Test
  fun `overloads that cover each member of a union parameter of the base method`() = test("""
    from typing import overload

    class A:
        def foo(self, x: int | str) -> int | str:
            return x

    class B(A):
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x
    """)

  // mypy and pyright accept this override, but a call `foo(1, "a")` matches no overload.
  @Test
  fun `overloads that cover only the diagonal of two union parameters of the base method`() = test("""
    from typing import overload

    class A:
        def foo(self, x: int | str, y: int | str) -> object:
            return x

    class B(A):
        @overload
        def foo(self, x: int, y: int) -> object: ...
        @overload
        def foo(self, x: str, y: str) -> object: ...
        def foo(self, x: int | str, y: int | str) -> object: # WARNING Signature of method 'B.foo()' does not match signature of the base method in class 'A'
            return x
    """)

  @Test
  fun `overloads that cover a union parameter with a return type that does not match the base method`() = test("""
    from typing import overload

    class A:
        def foo(self, x: int | str) -> int:
            return 0

    class B(A):
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str: # WARNING Signature of method 'B.foo()' does not match signature of the base method in class 'A'
            return x
    """)

  @Test
  fun `overloads that cover a keyword-only union parameter of the base method`() = test("""
    from typing import overload

    class A:
        def foo(self, *, x: int | str) -> object:
            return x

    class B(A):
        @overload
        def foo(self, *, x: int) -> object: ...
        @overload
        def foo(self, *, x: str) -> object: ...
        def foo(self, *, x: int | str) -> object:
            return x
    """)

  // mypy reports this override. pyright does not.
  @Test
  fun `overloads that drop the default value of a parameter of the base method`() = test("""
    from typing import overload

    class A:
        def foo(self, x: int | str = 0) -> object:
            return x

    class B(A):
        @overload
        def foo(self, x: int) -> object: ...
        @overload
        def foo(self, x: str) -> object: ...
        def foo(self, x: int | str = 0) -> object: # WARNING Signature of method 'B.foo()' does not match signature of the base method in class 'A'
            return x
    """)

  @Test
  fun `overloads over a base method with no return annotation that raises NotImplementedError`() = test("""
    from typing import overload

    class Base:
        def get(self, key: int | str):
            raise NotImplementedError

    class Impl(Base):
        @overload
        def get(self, key: int) -> int: ...
        @overload
        def get(self, key: str) -> str: ...
        def get(self, key: int | str) -> int | str:
            return key
    """)

  @Test
  fun `overloads over a base method with no return annotation and an ellipsis body`() = test("""
    from typing import overload

    class Base:
        def get(self, key: int | str): ...

    class Impl(Base):
        @overload
        def get(self, key: int) -> int: ...
        @overload
        def get(self, key: str) -> str: ...
        def get(self, key: int | str) -> int | str:
            return key
    """)

  @Test
  fun `classmethod and staticmethod overload groups`() = test("""
    from typing import overload

    class A:
        @overload
        @classmethod
        def c(cls, x: int) -> int: ...
        @overload
        @classmethod
        def c(cls, x: str) -> str: ...
        @classmethod
        def c(cls, x: int | str) -> int | str:
            return x

        @overload
        @staticmethod
        def s(x: int) -> int: ...
        @overload
        @staticmethod
        def s(x: str) -> str: ...
        @staticmethod
        def s(x: int | str) -> int | str:
            return x

    class B(A):
        @overload
        @classmethod
        def c(cls, x: int) -> int: ...
        @overload
        @classmethod
        def c(cls, x: str) -> str: ...
        @classmethod
        def c(cls, x: int | str) -> int | str:
            return x

        @overload
        @staticmethod
        def s(x: int) -> int: ...
        @overload
        @staticmethod
        def s(x: str) -> str: ...
        @staticmethod
        def s(x: int | str) -> int | str:
            return x

    class C(A):
        @classmethod
        def c(cls, x: int | str) -> int | str: # WARNING Signature of method 'C.c()' does not match signature of the base method in class 'A'
            return x

        @staticmethod
        def s(x: int | str) -> int | str: # WARNING Signature of method 'C.s()' does not match signature of the base method in class 'A'
            return x
    """)

  @Test
  fun `overloaded method of a generic base class`() = test("""
    from typing import overload
    from base import Base

    class Single(Base[int]):
        def g(self, x: int | None) -> int | None: # WARNING Signature of method 'Single.g()' does not match signature of the base method in class 'Base'
            return x

    class Compatible(Base[int]):
        @overload
        def g(self, x: int) -> int: ...
        @overload
        def g(self, x: None) -> None: ...
        def g(self, x: int | None) -> int | None:
            return x

    class Incompatible(Base[int]):
        @overload
        def g(self, x: str) -> str: ...
        @overload
        def g(self, x: None) -> None: ...
        def g(self, x: str | None) -> str | None: # WARNING Signature of method 'Incompatible.g()' does not match signature of the base method in class 'Base'
            return x
    """, "base.pyi" to """
    from typing import overload

    class Base[T]:
        @overload
        def g(self, x: T) -> T: ...
        @overload
        def g(self, x: None) -> None: ...
    """.trimIndent())

  // A stub file has no implementation, so the first @overload declaration anchors the group.
  @Test
  @TestCaseOptions(testFileName = "aaa.pyi")
  fun `overload groups in a stub file`() = test("""
    from typing import overload

    class A:
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...

    class B(A):
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...

    class C(A):
        @overload
        def foo(self, x: int) -> int: ... # WARNING Signature of method 'C.foo()' does not match signature of the base method in class 'A'
        @overload
        def foo(self, x: None) -> str: ...
    """)

  @Test
  fun `overload group with no implementation in a protocol`() = test("""
    from typing import Protocol, overload

    class A(Protocol):
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...

    class B(A, Protocol):
        @overload
        def foo(self, x: int) -> int: ... # WARNING Signature of method 'B.foo()' does not match signature of the base method in class 'A'
        @overload
        def foo(self, x: None) -> str: ...
    #       ^^^ WARNING Type of 'foo' is incompatible with 'A'
    """)

  // The decorator can change the signature of the overload, so the check does not compare the group.
  @Test
  fun `overload group with an unknown decorator on one overload`() = test("""
    from typing import overload

    def decorator(f):
        return f

    class A:
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        def foo(self, x: str) -> str: ...
        def foo(self, x: int | str) -> int | str:
            return x

    class B(A):
        @overload
        def foo(self, x: int) -> int: ...
        @overload
        @decorator
        def foo(self, x: None) -> str: ...
        def foo(self, x: int | None) -> int | str:
            return 0
    """)
}
