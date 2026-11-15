// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.fixtures.PyCodeInsightTestCase.TestInspections
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@Layers.Functional
@Subsystems.Inspections
@TestFor(classes = [PyNonOverlappingComparisonInspection::class], issues = ["PY-94128"])
@TestInspections(enableInspections = [PyNonOverlappingComparisonInspection::class])
class PyNonOverlappingComparisonInspectionTest : PyCodeInsightTestCase() {

  @Nested
  inner class Reported {
    @Test
    fun `identity with None`() = test("""
      def f(a: int):
          if a is None: ...  # WEAK_WARNING This comparison is always False because the types 'int' and 'None' do not overlap
          if a is not None: ...  # WEAK_WARNING This comparison is always True because the types 'int' and 'None' do not overlap
      """.trimIndent())

    @Test
    fun `equality of builtin types in different equality groups`() = test("""
      def f(s: str, b: bytes):
          if s == 0: ...  # WEAK_WARNING This comparison is always False because the types 'str' and 'Literal[0]' do not overlap
          if b == "a": ...  # WEAK_WARNING This comparison is always False because the types 'bytes' and 'Literal["a"]' do not overlap
          if s != b: ...  # WEAK_WARNING This comparison is always True because the types 'str' and 'bytes' do not overlap
      """.trimIndent())

    @Test
    fun `two different literals of one builtin class`() = test("""
      from typing import Literal

      def f(x: Literal["a"]):
          if x == "b": ...  # WEAK_WARNING This comparison is always False because the types 'Literal["a"]' and 'Literal["b"]' do not overlap

      def g(x: Literal["a"]):
          if x is "b": ...  # WEAK_WARNING This comparison is always False because the types 'Literal["a"]' and 'Literal["b"]' do not overlap
      """.trimIndent())

    @Test
    fun `membership in a display of items that do not overlap`() = test("""
      def f(s: str):
          if s in (1, 2): ...  # WEAK_WARNING This comparison is always False because the type 'str' does not overlap with the item type of 'tuple[Literal[1], Literal[2]]'
          if s not in [1, 2]: ...  # WEAK_WARNING This comparison is always True because the type 'str' does not overlap with the item type of 'list[int]'
      """.trimIndent())

    @Test
    fun `membership in a builtin container`() = test("""
      def f(s: str, numbers: list[int], keys: dict[int, str], frozen: frozenset[bytes]):
          if s in numbers: ...  # WEAK_WARNING This comparison is always False because the type 'str' does not overlap with the item type of 'list[int]'
          if s in keys: ...  # WEAK_WARNING This comparison is always False because the type 'str' does not overlap with the item type of 'dict[int, str]'
          if s in frozen: ...  # WEAK_WARNING This comparison is always False because the type 'str' does not overlap with the item type of 'frozenset[bytes]'
      """.trimIndent())

    @Test
    fun `membership in a homogeneous tuple or a dict display`() = test("""
      def f(s: str, numbers: tuple[int, ...]):
          if s in numbers: ...  # WEAK_WARNING This comparison is always False because the type 'str' does not overlap with the item type of 'tuple[int, ...]'
          if s in {1: "a"}: ...  # WEAK_WARNING This comparison is always False because the type 'str' does not overlap with the item type of 'dict[int, str]'
      """.trimIndent())

    @Test
    fun `membership of a literal in a display of other literals`() = test("""
      from typing import Literal

      def f(x: Literal["a"]):
          if x in ("b", "c"): ...  # WEAK_WARNING This comparison is always False because the type 'Literal["a"]' does not overlap with the item type of 'tuple[Literal["b"], Literal["c"]]'
      """.trimIndent())

    @Test
    fun `the highlight covers both operands`() = test("""
      def f(s: str):
          if (s in (1, 2)): ...
      #       ^^^^^^^^^^^ WEAK_WARNING This comparison is always False because the type 'str' does not overlap with the item type of 'tuple[Literal[1], Literal[2]]'
      """.trimIndent())

    @Test
    fun `equality with None`() = test("""
      def f(i: int):
          if i == None: ...  # WEAK_WARNING This comparison is always False because the types 'int' and 'None' do not overlap
      """.trimIndent())

    @Test
    fun `final classes with the default comparison`() = test("""
      from typing import final

      @final
      class A: ...

      @final
      class B: ...

      def f(fa: A, fb: B):
          if fa == fb: ...  # WEAK_WARNING This comparison is always False because the types 'A' and 'B' do not overlap
          if fa is fb: ...  # WEAK_WARNING This comparison is always False because the types 'A' and 'B' do not overlap
      """.trimIndent())

    @Test
    fun `a comparison outside of a condition`() = test("""
      def f(s: str) -> bool:
          return s == 0  # WEAK_WARNING This comparison is always False because the types 'str' and 'Literal[0]' do not overlap
      """.trimIndent())

    @Test
    fun `each comparison of a chain`() = test("""
      def f(i: int, j: int, s: str, t: str, numbers: list[int]):
          if i == j == s: ...
      #           ^^^^^^ WEAK_WARNING This comparison is always False because the types 'int' and 'str' do not overlap
          if i < j in numbers: ...
          if t < s in numbers: ...
      #          ^^^^^^^^^^^^ WEAK_WARNING This comparison is always False because the type 'str' does not overlap with the item type of 'list[int]'
      """.trimIndent())

    @Test
    fun `a plain enum and a string`() = test("""
      from enum import Enum

      class Color(Enum):
          RED = "red"

      def f(color: Color, name: str):
          if color == "red": ...  # WEAK_WARNING This comparison is always False because the types 'Color' and 'Literal["red"]' do not overlap
          if color == name: ...  # WEAK_WARNING This comparison is always False because the types 'Color' and 'str' do not overlap
      """.trimIndent())
  }

  @Nested
  inner class NotReported {
    @Test
    fun `a type that can define its own comparison`() = test("""
      from decimal import Decimal

      class Open: ...

      def f(d: Decimal, open: Open, s: str):
          if d == 0: ...
          if open == s: ...
          if s == open: ...
      """.trimIndent())

    @Test
    fun `a final class with its own comparison`() = test("""
      from typing import final

      @final
      class WithEq:
          def __eq__(self, other: object) -> bool: ...

      def f(with_eq: WithEq, s: str):
          if with_eq == s: ...
          if s == with_eq: ...
      """.trimIndent())

    @Test
    fun `types that overlap`() = test("""
      from typing import Literal

      class Base: ...

      class Derived(Base): ...

      def f(x: int | str, base: Base, derived: Derived, i: int, y: float, o: object, a: Literal["a"]):
          if x == "a": ...
          if base is derived: ...
          if i == y: ...
          if i is y: ...
          if o == 1: ...
          if a == "a": ...
      """.trimIndent())

    @Test
    fun `a bool literal and an int`() = test("""
      from typing import Literal

      def f(flag: Literal[True]):
          if flag == 1: ...
      """.trimIndent())

    @Test
    fun `a builtin enum mixin`() = test("""
      from enum import IntEnum, StrEnum

      class Number(IntEnum):
          ONE = 1

      class Name(StrEnum):
          A = "a"

      def f(number: Number, name: Name):
          if number == 1: ...
          if name == "a": ...
      """.trimIndent())

    @Test
    fun `a type variable, a protocol and a new type`() = test("""
      from typing import NewType, Protocol

      class Named(Protocol):
          name: str

      UserId = NewType("UserId", int)

      def f[T](t: T, named: Named, user_id: UserId, s: str):
          if t == s: ...
          if named == s: ...
          if named is s: ...
          if user_id == 1: ...
      """.trimIndent())

    @Test
    fun `an unknown type`() = test("""
      from typing import Any

      def f(x: Any, y, s: str):
          if x == s: ...
          if y == s: ...
          if s in x: ...
      """.trimIndent())

    @Test
    fun `a container that can define its own membership check`() = test("""
      from collections.abc import Sequence

      def f(s: str, sequence: Sequence[int], text: str):
          if s in sequence: ...
          if s in text: ...
      """.trimIndent())

    @Test
    fun `a display with an unpacked item or no items`() = test("""
      def f(s: str, numbers: list[int]):
          if s in (1, *numbers): ...
          if s in (): ...
      """.trimIndent())

    @Test
    fun `a class object`() = test("""
      def f(x: int):
          if type(x) == str: ...
          if int == str: ...
      """.trimIndent())
  }
}
