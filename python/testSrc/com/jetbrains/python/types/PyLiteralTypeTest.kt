// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for [literal types](https://typing.python.org/en/latest/spec/literal.html)
 * (`typing.Literal` / `typing_extensions.Literal`): literal inference and widening, unions of literals,
 * literal narrowing via `==`/`is`/`in`, and `Literal` in overloads and assignability checks.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyLiteralTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class LiteralAnnotationsAndTypeComments {
    @Test
    @TestFor(issues = ["PY-35235"])
    fun `Literal of bool annotation`() = test("""
      from typing_extensions import Literal
      expr: Literal[True] = False
      # │                   ^^^^^ WARNING Expected type 'Literal[True]', got 'Literal[False]' instead
      # └ TYPE Literal[True]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `empty Literal subscript annotation degrades to bool`() = test("""
      from typing_extensions import Literal
      expr: Literal[] = False
      #│            └ ERROR Expression expected
      #└ TYPE Literal[False]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `bare Literal annotation degrades to bool`() = test("""
      from typing_extensions import Literal
      expr: Literal = False
      #│    ^^^^^^^ WARNING 'Literal' must have at least one parameter
      #└ TYPE Literal[False]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `bool inferred without Literal annotation`() = test("""
      expr = False
      #└ TYPE Literal[False]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `Literal int type comment`() = test("""
      from typing_extensions import Literal
      expr = 20  # type: Literal[10]
      #│     ^^ WARNING Expected type 'Literal[10]', got 'Literal[20]' instead
      #└ TYPE Literal[10]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `Literal negative int type comment`() = test("""
      from typing_extensions import Literal
      expr = 20  # type: Literal[-10]
      # │    ^^ WARNING Expected type 'Literal[-10]', got 'Literal[20]' instead
      # └ TYPE Literal[-10]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `Literal of float is not a valid literal`() = test("""
      from typing_extensions import Literal
      expr = 20  # type: Literal[10.5]
      #│                         ^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
      #└ TYPE Literal[20]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `Literal of complex is not a valid literal`() = test("""
      from typing_extensions import Literal
      expr = 20  # type: Literal[10j]
      #│                         ^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
      #└ TYPE Literal[20]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `empty Literal subscript type comment degrades to inferred`() = test("""
      from typing_extensions import Literal
      expr = 20  # type: Literal[]
      # │                        └ ERROR Expression expected
      # └ TYPE Literal[20]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `bare Literal type comment degrades to inferred`() = test("""
      from typing_extensions import Literal
      expr = 20  # type: Literal
      #│                 ^^^^^^^ WARNING 'Literal' must have at least one parameter
      #└ TYPE Literal[20]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `int inferred without Literal type comment`() = test("""
      from typing_extensions import Literal
      expr = 20
      #└ TYPE Literal[20]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `Literal None annotation`() = test("""
      from typing_extensions import Literal
      expr: Literal[None] = undefined
      #│                    ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE None
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `Literal of enum member annotation`() = test("""
      from typing_extensions import Literal

      from enum import Enum

      class A(Enum):
          V1 = 1
          V2 = 2

      expr: Literal[A.V1] = undefined
      #│                    ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE Literal[A.V1]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `typing literal initialization`() = test("""
      from typing_extensions import Literal

      a: Literal[20] = 20
      b: Literal[30] = 25
      #                ^^ WARNING Expected type 'Literal[30]', got 'Literal[25]' instead
      c: Literal[2, 3, 4] = 3
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `typing literal initialization with different expressions`() = test("""
      from typing_extensions import Literal

      a1: Literal[0x14] = 20
      a2: Literal[20] = 0x14
      b1: Literal[0] = False
      #                ^^^^^ WARNING Expected type 'Literal[0]', got 'Literal[False]' instead
      b2: Literal[False] = 0
      #                    └ WARNING Expected type 'Literal[False]', got 'Literal[0]' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `explicit typing literal argument`() = test("""
      from typing_extensions import Literal

      a: Literal[20] = undefined # ERROR Unresolved reference 'undefined'
      b: Literal[30] = undefined # ERROR Unresolved reference 'undefined'
      c: int = 20

      def foo1(p1: Literal[20]):
          pass

      foo1(a)
      foo1(b)
      #    └ WARNING Expected type 'Literal[20]', got 'Literal[30]' instead
      foo1(c)
      #    └ WARNING Expected type 'Literal[20]', got 'int' instead

      def foo2(p1: int):
          pass

      foo2(a)
      foo2(b)
      foo2(c)
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `negative typing literals`() = test("""
      from typing_extensions import Literal
      a = undefined  # type: Literal[-10]
      #   ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      b = undefined  # type: Literal[-20]
      #   ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      a = b
      #   └ WARNING Expected type 'Literal[-10]', got 'Literal[-20]' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `distinguish typing literals from type hint or value`() = test("""
      from typing_extensions import Literal
      a = Literal[10]  # type: Literal[0]
      #   ^^^^^^^^^^^ WARNING Expected type 'Literal[0]', got 'type[Literal[10]]' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `keyword argument against typing literal`() = test("""
      from typing_extensions import Literal
      def f(a: Literal["b"]):
          pass
      f(a='b')
      f(a='c')
      # ^^^^^ WARNING Expected type 'Literal["b"]', got 'Literal['c']' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `numeric matching and typing literal`() = test("""
      from typing import Literal
      def expects_str(x: float) -> None: ...
      var: Literal[1] = 1
      expects_str(var)
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `non plain string as typing literal value`() = test("""
      from typing import Literal
      a: Literal["22"] = f"22"
      b: Literal["22"] = f"32"
      #                  ^^^^^ WARNING Expected type 'Literal["22"]', got 'Literal[f"32"]' instead
      two = "2"
      c: Literal["22"] = f"2{two}"
      #                  ^^^^^^^^^ WARNING Expected type 'Literal["22"]', got 'str' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235", "PY-42281"])
    fun `expected typing literal return type`() = test("""
      from typing import Literal
      def foo() -> Literal["ok"]:
          return "ok"
      """.trimIndent())
  }

  @Nested
  inner class UnionsOfLiterals {
    @Test
    @TestFor(issues = ["PY-35235"])
    fun `union of int literals`() = test("""
      from typing_extensions import Literal
      expr = undefined  # type: Literal[-1, 0, 1]
      #│     ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE Literal[-1, 0, 1]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `union of mixed literals`() = test("""
      from typing_extensions import Literal
      expr = undefined  # type: Literal[42, "foo", True]
      #│     ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE Literal[42, "foo", True]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `literal of nested literal references`() = test("""
      from typing_extensions import Literal
      a = Literal[1]
      b = Literal[2, 3]
      c = Literal[4, 5]
      d = Literal[b, c]
      expr = undefined  # type: Literal[a, d]
      #│     ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE Literal[1, 2, 3, 4, 5]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    fun `literal of nested literals collapses`() = test("""
      from typing_extensions import Literal
      expr = undefined  # type: Literal[Literal[Literal[1, 2], "foo"], 5, None]
      #│     ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE Literal[1, 2, "foo", 5] | None
      """.trimIndent())
  }

  @Nested
  inner class LiteralInOverloads {
    @Test
    @TestFor(issues = ["PY-40838"])
    fun `union of many overload return types including literals`() = test(
      // FIXME originally ran at Python 2.7 (PyTypeTest default) but uses Python-3-only
      //  return annotations and overloads; kept at latest where the body is meaningful.
      """
      from typing import overload, Literal
      
      @overload
      def foo1() -> Literal["1"]:
          pass
      
      @overload
      def foo1() -> Literal[2]:
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '() -> Literal[2]'
          pass
      
      @overload
      def foo1() -> bool:
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '() -> bool'
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 2 are the same or broaderConflicting signature: '() -> bool'
          pass
      
      @overload
      def foo1() -> None:
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 1 are the same or broaderConflicting signature: '() -> None'
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 2 are the same or broaderConflicting signature: '() -> None'
      #   ^^^^ WARNING This overload will never be matched as parameter types(s) of overload 3 are the same or broaderConflicting signature: '() -> None'
          pass
      
      def foo1()
      #         └ ERROR ':' expected
          pass
      
      expr = foo1()
      #└ TYPE Literal["1"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON36)
    fun `overload selected by literal argument`() = test("""
      from typing_extensions import Literal
      from typing import overload
      
      @overload
      def foo(p1: Literal["a"]) -> str: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["a"]) -> str'
      
      @overload
      def foo(p1: Literal["b"]) -> bytes: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["b"]) -> bytes'
      
      @overload
      def foo(p1: str) -> int: ...
      
      def foo(p1):
          pass
      
      a: Literal["a"]
      expr = foo(a)
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON36)
    fun `overload falls back to non-literal argument`() = test("""
      from typing_extensions import Literal
      from typing import overload
      
      @overload
      def foo(p1: Literal["a"]) -> str: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["a"]) -> str'
      
      @overload
      def foo(p1: Literal["b"]) -> bytes: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["b"]) -> bytes'
      
      @overload
      def foo(p1: str) -> int: ...
      
      def foo(p1):
          pass

      a: str = "a"
      expr = foo(a)
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-35235"])
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON36)
    fun `overload selected by literal string argument`() = test("""
      from typing_extensions import Literal
      from typing import overload
      
      @overload
      def foo(p1: Literal["a"]) -> str: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["a"]) -> str'
      
      @overload
      def foo(p1: Literal["b"]) -> bytes: ...
      #   ^^^ WARNING This overload overlaps overload 3 with incompatible return typeConflicting signature: '(p1: Literal["b"]) -> bytes'
      
      @overload
      def foo(p1: str) -> int: ...
      
      def foo(p1):
          pass
      
      expr = foo("a")
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-42473"])
    fun `overload literal enum imported`() = test("""
      from m import E, f

      a: int = f(E.b)
      b: str = f(E.a)
      """.trimIndent(), "m.py" to """
      from enum import Enum, auto
      from typing import Literal, overload

      class E(Enum):
          a = auto()
          b = auto()

      @overload
      def f(x: Literal[E.a]) -> str: ...

      @overload
      def f(x: Literal[E.b]) -> int: ...

      def f(x: E) -> object: ...
      """.trimIndent())
  }

  @Nested
  inner class LiteralNarrowingViaEquality {
    @Test
    fun `narrow str by equality to literal`() = test("""
      from typing import Literal
      def foo(v: str):
          if (v == "abba"):
              expr = v
      #       └ TYPE Literal["abba"]
      """.trimIndent())

    @Test
    fun `narrow literal union by inequality`() = test("""
      from typing import Literal
      def foo(v: Literal["abba", "ab"]):
          if (v != "abba"):
              expr = v
      #       └ TYPE Literal["ab"]
      """.trimIndent())

    @Test
    fun `narrow str by equality to literal variable`() = test("""
      from typing import Literal
      abc: Literal["abc"] = "abc"
      def foo(v: str):
          if (v == abc):
              expr = v
      #       └ TYPE Literal["abc"]
      """.trimIndent())

    @Test
    fun `narrow walrus by equality to literal`() = test("""
      from typing import Literal
      if ((v := input()) == "abba"):
          expr = v
      #   └ TYPE Literal["abba"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83625"])
    fun `narrow two literal unions by equality`() = test("""
      from typing import Literal
      def foo(v: Literal["a", "b"], w: Literal["b", "c"]):
          if (v == w):
              expr = v
      #       └ TYPE Literal["b"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83625"])
    fun `narrow two literal unions by inequality keeps full type`() = test("""
      from typing import Literal
      def foo(v: Literal["a", "b"], w: Literal["b", "c"]):
          if (v != w):
              expr = v
      #       └ TYPE Literal["a", "b"]
      """.trimIndent())
  }

  @Nested
  inner class LiteralNarrowingViaMembership {
    @Test
    fun `narrow int by membership in tuple`() = test("""
      def f(a: int):
          if a in (1, 2, ""):
              expr = a
      #       └ TYPE Literal[1, 2]
      """.trimIndent())

    @Test
    fun `narrow int by membership in set`() = test("""
      def f(a: int):
          if a in {1, 2, ""}:
              expr = a
      #       └ TYPE Literal[1, 2]
      """.trimIndent())

    @Test
    fun `narrow int by membership in list`() = test("""
      def f(a: int):
          if a in [1, 2, ""]:
              expr = a
      #       └ TYPE Literal[1, 2]
      """.trimIndent())

    @Test
    fun `narrow union by membership including enum member`() = test("""
      from enum import Enum
      class E(Enum):
          A = 1
      def f(a: int | str):
          if a in (-10, E.A, "a"):
              expr = a
      #       └ TYPE Literal[-10, "a"]
      """.trimIndent())

    @Test
    fun `narrow literal union by membership`() = test("""
      from typing import Literal
      def f(a: Literal[3, "abb", "ab", False]):
          if a in ("abb", True):
              expr = a
      #       └ TYPE Literal["abb"]
      """.trimIndent())

    @Test
    fun `narrow literal union by negated membership`() = test("""
      from typing import Literal
      def f(a: Literal[3, "abb", "ab", False]):
          if a not in ("abb", False):
              expr = a
      #       └ TYPE Literal[3, "ab"]
      """.trimIndent())

    @Test
    fun `narrow literal union in else of negated membership`() = test("""
      from typing import Literal
      def f(a: Literal[10, "abb", "ab", False]):
          if a not in ("abb", False):
              pass
          else:
              expr = a
      #       └ TYPE Literal["abb", False]
      """.trimIndent())

    @Test
    fun `narrow object by membership including None`() = test("""
      def f(v: object):
          if v in (-1, None):
              expr = v
      #       └ TYPE Literal[-1] | None
      """.trimIndent())

    @Test
    fun `narrow walrus by membership`() = test("""
      from typing import Literal
      if (a := input()) in ("abba", False):
          expr = a
      #   └ TYPE Literal["abba"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83625"])
    fun `narrow literal union by membership in literal variables`() = test("""
      from typing import Literal
      def foo(v: Literal["a", "b", "c"], u: Literal["b"], w: Literal["b", "c"]):
          if v in (u, w):
              expr = v
      #       └ TYPE Literal["b", "c"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83625"])
    fun `narrow literal union by negated membership in literal variables`() = test("""
      from typing import Literal
      def foo(v: Literal["a", "b", "c"], u: Literal["b"], w: Literal["b", "c"]):
          if v not in (u, w):
              expr = v
      #       └ TYPE Literal["a", "c"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83625"])
    fun `no membership narrowing for regular classes`() = test("""
      class A: pass

      class B(A): pass
      class C(A): pass

      def test(x: A, y: B, z: C):
          if x in [y, z]:
              expr = x
      #       └ TYPE A
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83625"])
    fun `no negated membership narrowing for regular classes`() = test("""
      class A: pass

      class B(A): pass
      class C(A): pass

      def test(x: A, y: B, z: C):
          if x not in [y, z]:
              expr = x
      #       └ TYPE A
      """.trimIndent())
  }

  @Nested
  inner class LiteralNarrowingViaIdentity {
    @Test
    @TestFor(issues = ["PY-83625"])
    fun `narrow two literal unions by identity`() = test("""
      from typing import Literal
      def foo(v: Literal["a", "b"], w: Literal["b", "c"]):
          if v is w:
              expr = v
      #       └ TYPE Literal["b"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-83625"])
    fun `narrow two literal unions by negated identity keeps full type`() = test("""
      from typing import Literal
      def foo(v: Literal["a", "b"], w: Literal["b", "c"]):
          if v is not w:
              expr = v
      #       └ TYPE Literal["a", "b"]
      """.trimIndent())

    @Test
    @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27, assertRecursionPrevention = false)
    fun `narrow literal union by legacy not equal operator`() = test("""
      from typing import Literal
      def foo(v: Literal["abba", "ab"]):
      #        └ ERROR Type annotations are unsupported in Python 2
          if (v <> "abba"):
              expr = v
      #       └ TYPE Literal["ab"]
      """.trimIndent())
  }

  @Nested
  inner class LiteralInferenceAndWidening {
    @Test
    @TestFor(issues = ["PY-77937"])
    fun `list of int literals widens to int`() = test("""
      from typing import Literal
      
      num1: Literal[1] = 1
      num2: Literal[2] = 2
      expr = [num1, num2]
      #└ TYPE list[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-77937"])
    fun `list of mixed literals widens to union`() = test("""
      from typing import Literal
      
      e1: Literal[1] = 1
      e2: Literal["abc"] = "abc"
      expr = [e1, e2]
      #└ TYPE list[int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-77937"])
    @TestCaseOptions(enableWeakWarnings = false)
    fun `list of literal and literalstring widens to union`() = test(
      // weak warnings disabled: with literal inference on, the `|` chain in the annotation
      // triggers a spurious `__or__` weak-warning unrelated to the inferred element type
      """
      from typing import Literal, LiteralString

      e: Literal[1, "ab"] | LiteralString | Literal["x"] = "abb"
      expr = [e]
      #└ TYPE list[int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-77937"])
    fun `set of int literals widens to int`() = test("""
      from typing import Literal
      
      num1: Literal[1] = 1
      num2: Literal[2] = 2
      expr = {num1, num2}
      #└ TYPE set[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-77937"])
    fun `set of mixed literals widens to union`() = test("""
      from typing import Literal
      
      e1: Literal[1] = 1
      e2: Literal["abc"] = "abc"
      expr = {e1, e2}
      #└ TYPE set[int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-77937"])
    @TestCaseOptions(enableWeakWarnings = false)
    fun `set of literal and literalstring widens to union`() = test(
      // weak warnings disabled: see `list of literal and literalstring widens to union`
      """
      from typing import Literal, LiteralString

      e: Literal[1, "ab"] | LiteralString | Literal["x"] = "abb"
      expr = {e}
      #└ TYPE set[int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-77937"])
    fun `dict of literals widens key and value`() = test("""
      from typing import Literal
      
      k1: Literal[1] = 1
      v1: Literal["2"] = "1"
      #                  ^^^ WARNING Expected type 'Literal["2"]', got 'Literal["1"]' instead
      k2: Literal[2] = 2
      v2: Literal["2"] = "2"
      expr = {k1: v1, k2: v2}
      #└ TYPE dict[int, str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-77937"])
    fun `dict of mixed literals widens to unions`() = test("""
      from typing import Literal, LiteralString
      
      k1: Literal[1] = 1
      v1: Literal["ab"] = "ab"
      k2: LiteralString = "k2"
      v2: Literal[True] = True
      expr = { k1: v1, k2: v2 }
      #└ TYPE dict[int | str, str | bool]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-77937"])
    @TestCaseOptions(enableWeakWarnings = false)
    fun `dict of literal unions widens to unions`() = test(
      // weak warnings disabled: see `list of literal and literalstring widens to union`
      """
      from typing import Literal, LiteralString

      k: Literal[1, "ab"] | LiteralString | Literal["x"] = "abb"
      v: Literal[1, "ab"] | LiteralString | Literal["x"] = 1
      expr = {k: v}
      #└ TYPE dict[int | str, int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-78125"])
    fun `dict with plain string key and literal value`() = test("""
      from typing import Literal
      
      v: Literal[1, "ab"] = 1
      expr = {"abb": v}
      #└ TYPE dict[str, int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-78125"])
    fun `dict with inferred string key and literal value`() = test("""
      from typing import Literal
      
      k = "abb"
      v: Literal[1, "ab"] = 1
      expr = {k: v}
      #└ TYPE dict[str, int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-78125"])
    fun `dict with literal string key and literal value`() = test("""
      from typing import Literal
      
      k: Literal["abb"] = "abb"
      v: Literal[1, "ab"] = 1
      expr = {k: v}
      #└ TYPE dict[str, int | str]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-78125"])
    fun `dict with literalstring key and literal value`() = test("""
      from typing import Literal, LiteralString
      
      k: LiteralString = "k"
      v: Literal[1, "ab"] = 1
      expr = {k: v}
      #└ TYPE dict[str, int | str]
      """.trimIndent())

    @Test
    fun `literal widens to int on augmented assignment`() = test("""
      from typing import Literal
      
      one: Literal[1] = 1
      x = one
      x += 1
      expr = x
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `literal preserved when imported via star`() = test("""
      from m import *
      expr = foo
      #└ TYPE Literal[1]
      """.trimIndent(),
      "m.py" to "foo = 1",
    )
  }

  @Nested
  inner class InspectionsOnLiteralTypes {
    @Test
    fun `literal string and bytes assignability`() = test("""
      from typing_extensions import Literal
      
      a: Literal["abc"] = undefined
      #                   ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      b: Literal[b"abc"] = undefined
      #                    ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      
      def foo1(p1: Literal["abc"]):
          pass
      foo1(a)
      foo1(b) # WARNING Expected type 'Literal["abc"]', got 'Literal[b"abc"]' instead
      
      def foo2(p1: Literal[b"abc"]):
          pass
      foo2(a) # WARNING Expected type 'Literal[b"abc"]', got 'Literal["abc"]' instead
      foo2(b)
      
      def foo3(p1: str):
          pass
      foo3(a)
      foo3(b) # WARNING Expected type 'str', got 'Literal[b"abc"]' instead
      
      def foo4(p1: bytes):
          pass
      foo4(a) # WARNING Expected type 'bytes', got 'Literal["abc"]' instead
      foo4(b)
      """.trimIndent())

    @Test
    fun `Final variable or attribute inferred as literal`() = test("""
      from typing import Literal, Final
      
      foo: Final = 3
      def expects_three(x: Literal[3]) -> None: ...
      
      expects_three(foo)
      
      def bar():
          var: Final = 3
          expects_three(var)
      """.trimIndent())

    @Test
    fun `Final list variable is not over-narrowed to literal`() = test("""
      from typing import Literal, Final
      v: Final = [1, 2]
      def expects_list(l: list[int]): ...
      
      expects_list(v)
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-79733"])
    fun `literal type inferred for comprehensions`() = test("""
      from typing import Literal


      def func(strings: list[str]):
          l1: list[Literal[1]] = [1 for x in strings]
          l2: list[Literal[1]] = [2 for x in strings] # WARNING Expected type 'list[Literal[1]]', got 'list[Literal[2]]' instead
          s1: set[Literal[1]] = {1 for x in strings}
          s2: set[Literal[1]] = {2 for x in strings} # WARNING Expected type 'set[Literal[1]]', got 'set[Literal[2]]' instead
          d1: dict[str, Literal[1]] = {x: 1 for x in strings}
          d2: dict[str, Literal[1]] = {x: 2 for x in strings} # WARNING Expected type 'dict[str, Literal[1]]', got 'dict[str, Literal[2]]' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-75556"])
    fun `literal type on kwargs`() = test("""
      from typing import Literal
      
      def f(**kwargs: Literal[1]): ...
      f(a=1)
      f(a=2) # WARNING Expected type 'Literal[1]', got 'Literal[2]' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-61137"])
    fun `literal type in conditional statements and expressions`() = test("""
      from typing import Literal
      def condition1():
          pass
      def return_literal_string() -> Literal["foo", "bar"]:
          return "foo" if condition1() else "bar"  # OK
      def return_literal_str2(literal_string: Literal["foo"]) -> Literal["foo"]:
          return "foo" if condition1() else literal_string  # OK
      """.trimIndent())
  }

  @Nested
  inner class LiteralInferenceForLiteralExpressions {

    @Test
    @TestFor(issues = ["PY-46450"])
    fun `int literal expression inferred as literal by default`() = test("""
      expr = 1
      #└ TYPE Literal[1]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    fun `bool literal expression inferred as literal by default`() = test("""
      expr = True
      #└ TYPE Literal[True]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    fun `str literal expression inferred as literal by default`() = test("""
      expr = "s"
      #└ TYPE Literal["s"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    fun `literal inference for literal expressions can be disabled by registry`() {
      val disposable = Disposer.newDisposable("PY-46450 literal-types-for-literals registry")
      try {
        Registry.get("python.typing.literal.types.for.literals").setValue(false, disposable)
        test("""
          expr = 1
          #└ TYPE int
          """.trimIndent())
      }
      finally {
        Disposer.dispose(disposable)
      }
    }

    @Test
    @TestFor(issues = ["PY-46450"])
    @TestCaseOptions
    fun `class attribute accessed via self is not over-narrowed to literal`() = test(
      // enablePyAnyType=false: attribute inference still degrades to Unknown under the py-any migration
      """
      class A:
          a = 1
          def f(self):
              expr = self.a
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    @TestCaseOptions
    fun `instance attribute accessed via self is not over-narrowed to literal`() = test(
      // enablePyAnyType=false: attribute inference still degrades to Unknown under the py-any migration
      """
      class A:
          def __init__(self):
              self.foo = 42

          def f(self):
              expr = self.foo
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    @TestCaseOptions
    fun `class attribute accessed via class is not over-narrowed to literal`() = test(
      // enablePyAnyType=false: attribute inference still degrades to Unknown under the py-any migration
      """
      class A:
          a = 1
      expr = A.a
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    fun `literal argument to generic constructor widens type parameter`() = test("""
      class A[T]:
          def __init__(self, t: T, *x_): ...
      expr = A(1, 1)
      #└ TYPE A[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    fun `literal in list comprehension widens to int`() = test("""
      def f(): return 1

      expr = [f() for x in []]
      #└ TYPE list[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    @TestCaseOptions
    fun `literal in tuple from generator stays literal`() = test(
      // enablePyAnyType=false: generator element inference still degrades to Unknown under the py-any migration
      """
      def f():
          return 1

      expr = tuple(f() for x in [])
      #└ TYPE tuple[Literal[1], ...]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80353"])
    fun `literal in class body inferred as literal`() = test("""
      class A:
          a = 1
          expr = a
      #     └ TYPE int FIXME Literal[1]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    fun `literal captured by TypeVarTuple stays literal`() = test("""
      def f[*Ts](*t: *Ts) -> tuple[*Ts]: ...
      expr = f(1)
      #└ TYPE tuple[int] FIXME tuple[Literal[1]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-46450"])
    fun `literal TypeVar bound propagates to result`() = test("""
      from typing import Literal

      def f[T: Literal[1]](t: T) -> list[T]: ...
      expr = f(1)
      #└ TYPE list[Literal[1]]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-38065"])
    fun `tuple literal against typing literal`() = test("""
      from typing import Literal, Tuple, List, Set

      L1 = Literal['test']
      L2 = Literal['a', 'b', 5]

      tuple_one_literal: Tuple[L1] = ('test',)
      tuple_one_literal_incorrect: Tuple[L1] = ('t',)
      #                                        ^^^^^^ WARNING Expected type 'tuple[Literal['test']]', got 'tuple[Literal['t']]' instead

      tuple_union_literal: Tuple[L2] = ('b',)
      tuple_union_literal_incorrect: Tuple[L2] = ('t',)
      #                                          ^^^^^^ WARNING Expected type 'tuple[Literal['a', 'b', 5]]', got 'tuple[Literal['t']]' instead

      tuple_several_literal: Tuple[L2, L1] = ('b', 'test')
      tuple_several_literal_incorrect: Tuple[L2, L1] = ('a', 'r')
      #                                                ^^^^^^^^^^ WARNING Expected type 'tuple[Literal['a', 'b', 5], Literal['test']]', got 'tuple[Literal['a'], Literal['r']]' instead

      tuple_of_list_and_tuple_set: Tuple[List[Tuple[L2, L1]], Set[L2]] = ([('b', 'test'), (5, 'test')], {'b', 5})
      tuple_of_list_and_tuple_set_incorrect: Tuple[List[Tuple[L2, L1]], Set[L2]] = ([('r', 'test'), (5, 'test')], {'b', 5})
      #                                                                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'tuple[list[tuple[Literal['a', 'b', 5], Literal['test']]], set[Literal['a', 'b', 5]]]', got 'tuple[list[tuple[Literal['r'], Literal['test']] | tuple[Literal[5], Literal['test']]], set[Literal['b', 5]]]' instead

      FooTuple = Tuple[Literal["a", "b"], int]

      def foo(param: int) -> FooTuple:
          return "a", param
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-41268"])
    fun `list literal against typing literal`() = test("""
      from typing import Literal, Tuple, List, Union

      L1 = Literal['test']
      L2 = Literal['a', 'b', 5]

      list_one_literal: List[L1] = ['test', 'test']
      list_one_literal_incorrect: List[L1] = ['r', 'a']
      #                                      ^^^^^^^^^^ WARNING Expected type 'list[Literal['test']]', got 'list[Literal['r', 'a']]' instead

      list_several_literal: List[L2] = ['a', 5]
      list_several_literal_incorrect: List[L2] = ['r', 'a']
      #                                          ^^^^^^^^^^ WARNING Expected type 'list[Literal['a', 'b', 5]]', got 'list[Literal['r', 'a']]' instead
      list_several_literal_incorrect: List[L2] = ['a', 'r']
      #                                          ^^^^^^^^^^ WARNING Expected type 'list[Literal['a', 'b', 5]]', got 'list[Literal['a', 'r']]' instead

      list_union_literal: List[Union[L2, L1]] = ['b', 'test', 5]
      list_union_literal_incorrect: List[Union[L2, L1]] = ['r', 'a']
      #                                                   ^^^^^^^^^^ WARNING Expected type 'list[Literal['a', 'b', 5, 'test']]', got 'list[Literal['r', 'a']]' instead
      list_union_literal_incorrect: List[Union[L2, L1]] = ['a', 'r']
      #                                                   ^^^^^^^^^^ WARNING Expected type 'list[Literal['a', 'b', 5, 'test']]', got 'list[Literal['a', 'r']]' instead

      list_tuple: List[Tuple[L2, L1]] = [('b', 'test'), (5, 'test')]
      list_tuple_incorrect: List[Tuple[L2, L1]] = [('a',), (5, 'test')]
      #                                           ^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'list[tuple[Literal['a', 'b', 5], Literal['test']]]', got 'list[tuple[Literal['a']] | tuple[Literal[5], Literal['test']]]' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-41268"])
    fun `set literal against typing literal`() = test("""
      from typing import Literal, Set, List, Union, Tuple

      L1 = Literal['test']
      L2 = Literal['a', 'b', 5]

      set_one_literal: Set[L1] = {'test', 'test'}
      set_one_literal_incorrect: Set[L1] = {'r', 'a'}
      #                                    ^^^^^^^^^^ WARNING Expected type 'set[Literal['test']]', got 'set[Literal['r', 'a']]' instead

      set_several_literal: Set[L2] = {'b', 5}
      set_several_literal_incorrect: Set[L2] = {'r', 'a'}
      #                                        ^^^^^^^^^^ WARNING Expected type 'set[Literal['a', 'b', 5]]', got 'set[Literal['r', 'a']]' instead
      set_several_literal_incorrect2: Set[L2] = {'a', 'r'}
      #                                         ^^^^^^^^^^ WARNING Expected type 'set[Literal['a', 'b', 5]]', got 'set[Literal['a', 'r']]' instead

      set_union_literal: Set[Union[L2, L1]] = {'b', 'test', 5}
      set_union_literal_incorrect: Set[Union[L2, L1]] = {'r', 'a'}
      #                                                 ^^^^^^^^^^ WARNING Expected type 'set[Literal['a', 'b', 5, 'test']]', got 'set[Literal['r', 'a']]' instead
      set_union_literal_incorrect2: Set[Union[L2, L1]] = {'a', 'r'}
      #                                                  ^^^^^^^^^^ WARNING Expected type 'set[Literal['a', 'b', 5, 'test']]', got 'set[Literal['a', 'r']]' instead

      set_of_tuple_and_list: Set[Union[Tuple[L2, L1], List[L1]]] = {('b', 'test'), ['test', 'test'], (5, 'test')}
      set_of_tuple_and_list_incorrect: Set[Union[Tuple[L2, L1], List[L1]]] = {('b', 'r'), ['test', 'test'], (5, 'test')}
      #                                                                      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'set[tuple[Literal['a', 'b', 5], Literal['test']] | list[Literal['test']]]', got 'set[tuple[Literal['b'], Literal['r']] | list[Literal['test']] | tuple[Literal[5], Literal['test']]]' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-41578"])
    fun `dict literal against typing literal`() = test("""
      from typing import Tuple, Literal, Dict, List

      L1 = Literal['k1', 'k2']
      L2 = Literal['a', 'b', 5]

      d1: Dict[L1, L2] = {'k1': 'b', 'k2': 5}
      d2: Dict[L1, L2] = {'k1': 'r'}
      #                  ^^^^^^^^^^^ WARNING Expected type 'dict[Literal['k1', 'k2'], Literal['a', 'b', 5]]', got 'dict[Literal['k1'], Literal['r']]' instead
      d3: Dict[L1, L2] = {'r': 'b'}
      #                  ^^^^^^^^^^ WARNING Expected type 'dict[Literal['k1', 'k2'], Literal['a', 'b', 5]]', got 'dict[Literal['r'], Literal['b']]' instead
      d4: Dict[L1, List[L2]] = {'k2': ['b', 5]}
      d5: Dict[L1, List[L2]] = {'k2': ['r', 5]}
      #                        ^^^^^^^^^^^^^^^^ WARNING Expected type 'dict[Literal['k1', 'k2'], list[Literal['a', 'b', 5]]]', got 'dict[Literal['k2'], list[Literal['r', 5]]]' instead
      d6: Dict[L1, Tuple[L2, L2]] = {'k2': ('a', 5)}
      d7: Dict[L1, Tuple[L2, L2]] = {'k2': ('r', 5)}
      #                             ^^^^^^^^^^^^^^^^ WARNING Expected type 'dict[Literal['k1', 'k2'], tuple[Literal['a', 'b', 5], Literal['a', 'b', 5]]]', got 'dict[Literal['k2'], tuple[Literal['r'], Literal[5]]]' instead
      d8: Dict[L1, Dict[L1, L2]] = {'k1': {'k2': 'a', 'k1': 5}}
      d9: Dict[L1, Dict[L1, L2]] = {'k1': {'k2': 'r', 'k1': 9}}
      #                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'dict[Literal['k1', 'k2'], dict[Literal['k1', 'k2'], Literal['a', 'b', 5]]]', got 'dict[Literal['k1'], dict[Literal['k2', 'k1'], Literal['r', 9]]]' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-48799"])
    fun `dict literal in variable`() = test("""
      from typing import TypedDict
      class C(TypedDict):
          foo: str
      def f(x: C) -> None:
          pass
      y = {}
      z = {'foo': 'bar'}
      n = {"foo": "", "quux": 3}
      f(y)
      # └ WARNING Expected type 'C', got 'dict[Unknown, Unknown]' instead
      f(n)
      # └ WARNING Expected type 'C', got 'dict[str, str | int]' instead
      f(z)
      # └ WARNING Expected type 'C', got 'dict[str, str]' instead
      f(x=y)
      # ^^^ WARNING Expected type 'C', got 'dict[Unknown, Unknown]' instead
      f(x=n)
      # ^^^ WARNING Expected type 'C', got 'dict[str, str | int]' instead
      f(x=z)
      # ^^^ WARNING Expected type 'C', got 'dict[str, str]' instead
      z2: C = y
      #       └ WARNING Expected type 'C', got 'dict[Unknown, Unknown]' instead
      z2: C = n
      #       └ WARNING Expected type 'C', got 'dict[str, str | int]' instead
      z2: C = z
      #       └ WARNING Expected type 'C', got 'dict[str, str]' instead

      """.trimIndent())
  }
}
