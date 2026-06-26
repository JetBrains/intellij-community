// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type-checker tests for subtyping and assignability: assignment/argument compatibility checks,
 * variance of supertypes, widening of inferred types, parameter defaults, descriptor `__set__`
 * assignment, augmented assignment, stdlib assignability (`os.PathLike`, `bytes`/`bytearray`,
 * `Supports*`), `Annotated`, intersection types and type narrowing in assignment contexts.
 */
class PySubtypingTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class AssignmentCompatibility {

    @Test
    @TestFor(issues = ["PY-24832"])
    fun `assignment with annotation`() = test("""
      def f():
          x1: int = 'foo' # WARNING Expected type 'int', got 'Literal["foo"]' instead
          x2: str = 'bar'
          x3: int = 0
          x4: str = 1 # WARNING Expected type 'str', got 'Literal[1]' instead
      """)

    @Test
    @TestFor(issues = ["PY-24832"])
    fun `reassignment respects declared type`() = test("""
      def f1():
          x: int = 0
          x = 'foo' # WARNING Expected type 'int', got 'Literal["foo"]' instead
          x = 1
          x = 'bar' # WARNING Expected type 'int', got 'Literal["bar"]' instead
          y: str = 'foo'
          y = 'bar'
          y = 0 # WARNING Expected type 'str', got 'Literal[0]' instead
          z: int
          z: str
          z = 1 # WARNING Expected type 'str', got 'Literal[1]' instead
          z = "aba"


      def f2(p: int):
          p = "aba" # TODO (PY-85871): Expected type 'int', got 'str' instead


      def f3(p: int):
          p: str = "aba"


      def f4(p: int):
          p: str
          p = "aba"


      v_global: int


      def outer():
          global v_global
          v_global = "abb" # WARNING Expected type 'int', got 'Literal["abb"]' instead

          v: int

          def inner():
              nonlocal v
              v = "abb" # WARNING Expected type 'int', got 'Literal["abb"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-24832"])
    fun `type declaration and assignment`() = test("""
      def f():
          x: int
          x = 'foo' # WARNING Expected type 'int', got 'Literal["foo"]' instead
          y: str
          y = 'bar'
      """)

    @Test
    @TestFor(issues = ["PY-24832"])
    fun `class level assignment`() = test("""
      from typing import ClassVar


      class C:
          x: int
          y: int = 0
          z: int = 'foo' # WARNING Expected type 'int', got 'Literal["foo"]' instead
          class_var: ClassVar[int]

          def f(self):
              self.x = 1
              self.x = 'bar' # WARNING Expected type 'int', got 'Literal["bar"]' instead
              self.y = 1
              self.y = 'bar' # WARNING Expected type 'int', got 'Literal["bar"]' instead
              self.z = 1
              self.z = 'bar' # WARNING Expected type 'int', got 'Literal["bar"]' instead

              self.class_var = 1
      #       ^^^^^^^^^^^^^^ WARNING Cannot assign to class variable 'class_var' via instance
              self.class_var = 'bar'
      #       │                ^^^^^ WARNING Expected type 'int', got 'Literal["bar"]' instead
      #       ^^^^^^^^^^^^^^ WARNING Cannot assign to class variable 'class_var' via instance
              C.class_var = 1
              C.class_var = 'bar' # WARNING Expected type 'int', got 'Literal["bar"]' instead
      """)

    @Test
    fun `no type mismatch in assignment without type annotation`() = test("""
      def f():
          x = 0
          x = 'foo'  # OK
          y = 'bar'
          y = 1  # OK
      """)
  }

  @Nested
  inner class AugmentedAssignment {

    @Test
    fun `augmented assignment argument types`() {
      test("""
        class A:
            def __iadd__(self, other: int) -> str: ...

        a = A()
        a += 1

        a = A()
        a += "a" # WARNING Expected type 'int', got 'Literal["a"]' instead
        """)
    }

    @Test
    fun `augmented assignment via __add__`() = test("""
      class A:
          def __add__(self, other: int) -> str: ...

      a = A()
      a += 1

      a = A()
      a += "a" # WARNING Expected type 'int', got 'Literal["a"]' instead
      """)

    @Test
    fun `augmented assignment via __radd__`() = test("""
      class A: pass

      class B:
          def __radd__(self, other: A) -> str: ...

      a = A()
      a += B()
      """)

    @Test
    @TestFor(issues = ["PY-6426"])
    fun `augmented assignment result type`() = test("""
      class A:
          def __iadd__(self, other: int) -> str: ...

      a: A = A()
      a += 1 # WARNING Expected type 'A' for augmented assignment, got 'str' from operation instead
      """)

    @Test
    @TestFor(issues = ["PY-6426"])
    fun `augmented assignment qualified target`() = test("""
      class A:
          i: int

      a: A = A()
      a.i += 1
      a.i += "s" # WARNING Expected type 'int', got 'Literal["s"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-6426"])
    fun `augmented assignment qualified target name collision`() = test("""
      class A:
          a: int

      a: A = A()
      a.a += 1
      a.a += "s" # WARNING Expected type 'int', got 'Literal["s"]' instead
      """)
  }

  @Nested
  inner class DescriptorSetAssignment {

    @Test
    @TestFor(issues = ["PY-76399"])
    fun `assigned value matches descriptor __set__`() = test("""
      class MyDescriptor:

          def __set__(self, obj, value: str) -> None:
              ...

      class Test:
          member: MyDescriptor

      t = Test()
      t.member = "str"
      t.member = 123 # WARNING Expected type 'str' (from '__set__'), got 'Literal[123]' instead
      t.member = list # WARNING Expected type 'str' (from '__set__'), got 'type[list]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76399"])
    fun `assigned value matches generic descriptor __set__`() = test("""
      class MyDescriptor[T]:

          def __set__(self, obj, value: T) -> None:
              ...

      class Test:
          member: MyDescriptor[str]

      t = Test()
      t.member = "str"
      t.member = 123 # WARNING Expected type 'str' (from '__set__'), got 'Literal[123]' instead
      t.member = list # WARNING Expected type 'str' (from '__set__'), got 'type[list]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76399"])
    fun `assigned value matches descriptor __set__ with literal value`() = test("""
      from typing import Literal


      class MyDescriptor:
          def __set__(self, obj, value: Literal[42]) -> None:
              ...

      class Test:
          member: MyDescriptor

      t = Test()
      t.member = 42
      t.member = 43 # WARNING Expected type 'Literal[42]' (from '__set__'), got 'Literal[43]' instead
      t.member = "42" # WARNING Expected type 'Literal[42]' (from '__set__'), got 'Literal["42"]' instead
      """)
  }

  @Nested
  inner class ParameterDefaults {

    @Test
    fun `parameter default value type`() = test("""
      from typing import Literal

      def f(
          a: str = "ok",
          b: int = "not ok", # WARNING Expected type 'int', got 'Literal["not ok"]' instead
          c: Literal[True] = True,
          d: Literal[True] = False # WARNING Expected type 'Literal[True]', got 'Literal[False]' instead
      ): ...
      """)

    @Test
    fun `ellipsis default argument in method`() = test("""
      class A:
          def f(self, a: str = ...): # WARNING Expected type 'str', got 'EllipsisType' instead
              pass
      """)

    @Test
    fun `invalid default None`() = test("""
      def f(a: str = None): # WARNING Expected type 'str', got 'None' instead
          print(a)
      """)

    @Test
    @TestFor(issues = ["PY-87997"])
    fun `parameter sentinel default value`() = test("""
      SENTINEL = object()

      def f(a: int = SENTINEL): ...

      f(1)
      f(SENTINEL)

      ANOTHER_SENTINEL = object()
      COPIED_SENTINEL = SENTINEL
      f(ANOTHER_SENTINEL) # WARNING Expected type 'int | SENTINEL', got 'ANOTHER_SENTINEL' instead
      f(COPIED_SENTINEL) # WARNING Expected type 'int | SENTINEL', got 'COPIED_SENTINEL' instead
      f(object()) # WARNING Expected type 'int | SENTINEL', got 'object' instead

      _: object = object()
      _: int = object() # WARNING Expected type 'int', got 'object' instead

      _ = SENTINEL
      _: object = SENTINEL
      _: int = SENTINEL # WARNING Expected type 'int', got 'SENTINEL' instead
      """)
  }

  @Nested
  inner class NoSubtypeIssueOnCreationExpression {

    @Test
    @TestFor(issues = ["PY-89564"])
    fun `no subtype issue on creation expression in return`() = test("""
      def f() -> list[object]:
         return [1, 2]
      """)

    @Test
    @TestFor(issues = ["PY-89564"])
    fun `no subtype issue on creation expression as argument`() = test("""
      def f(a: list[object]): ...
      f([1, 2])
      """)

    @Test
    @TestFor(issues = ["PY-89564"])
    fun `no subtype issue on creation expression assignment value`() = test("""
      x: list[object] = [1, 2]
      """)

    @Test
    @TestFor(issues = ["PY-89564"])
    fun `no subtype issue on creation expression parameter default`() = test("""
      def f(a: list[object] = [1, 2]) -> None:
          pass
      """)

    @Test
    @TestFor(issues = ["PY-89564"])
    fun `no subtype issue on creation expression keyword only parameter default`() = test("""
      def f(*, a: list[object] = [1, 2]) -> None:
          pass
      """)

    @Test
    @TestFor(issues = ["PY-89564"])
    fun `no subtype issue on nested creation expression parameter default`() = test("""
      def f(a: list[list[object]] = [[1, 2]]) -> None:
          pass
      """)

    @Test
    fun `tuple in generic explicit is valid`() = test("""
      from typing import Literal

      class A[T]:
          def __init__(self, t: T): ...

      A[list[tuple[Literal[1]]]]([(1,)])

      _: list[tuple[Literal[1]]] = [(1,)]
      _: list[tuple[int]] = [(1,)]
      """)
  }

  @Nested
  inner class VarPositionalKeywordParamAssignment {

    @Test
    @TestFor(issues = ["PY-86902"])
    fun `var positional param assignment`() = test("""
      def f(*args: str, argv: tuple[str, ...]) -> None:
          args = argv
      """)

    @Test
    @TestFor(issues = ["PY-86902"])
    fun `var keyword param assignment`() = test("""
      def f(args: dict[str, str], **kwargs: str) -> None:
          kwargs = args
      """)
  }

  @Nested
  inner class VarianceOfSupertypes {

    @Test
    @TestFor(issues = ["PY-76814"])
    fun `supertypes of covariance`() = test("""
      class D[T]:
          def get(self) -> T: pass

      d_int : D[int] = D[int]()
      d_obj : D[object] = D[object]()

      d_int = d_obj # E
      #│      ^^^^^ WARNING Expected type 'D[int]', got 'D[object]' instead
      #^^^^ WARNING Redeclared 'd_int' defined above without usage
      d_obj = d_int # ok
      """)

    @Test
    @TestFor(issues = ["PY-76814"])
    fun `supertypes of covariance with outer subtype`() = test("""
      class D[T]:
          def get(self) -> T: pass
      class E(D[int]): ...

      d_int : E = E()
      d_obj : D[object] = D[object]()

      d_int = d_obj # E
      #│      ^^^^^ WARNING Expected type 'E', got 'D[object]' instead
      #^^^^ WARNING Redeclared 'd_int' defined above without usage
      d_obj = d_int # ok
      """)

    @Test
    @TestFor(issues = ["PY-76814"])
    fun `supertypes of contravariance`() = test("""
      class D[T]:
          def set(self, val: T): pass

      d_int : D[int] = D[int]()
      d_obj : D[object] = D[object]()

      d_int = d_obj # ok
      #│      ^^^^^ WARNING Expected type 'D[int]', got 'D[object]' instead FIXME # PY-89564
      #^^^^ WARNING Redeclared 'd_int' defined above without usage
      d_obj = d_int # E
      #       ^^^^^ WARNING Expected type 'D[object]', got 'D[int]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76814"])
    fun `supertypes of contravariance with outer subtype`() = test("""
      class D[T]:
          def set(self, val: T): pass
      class E(D[object]): ...

      d_int : D[int] = D[int]()
      d_obj : E = E()

      d_int = d_obj # ok
      #│      ^^^^^ WARNING Expected type 'D[int]', got 'E' instead FIXME # PY-89564
      #^^^^ WARNING Redeclared 'd_int' defined above without usage
      d_obj = d_int # E
      #       ^^^^^ WARNING Expected type 'E', got 'D[int]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76814"])
    fun `supertypes of mixed variance covariant ok`() = test("""
      class A[T]: # T is covariant
          def get(self) -> T: ...

      class B[S](A[S]): # S is invariant
          def set(self, s: S): ...
          def get(self) -> S: ...

      a : A[object] = A[object]()
      b : B[int] = B[int]()

      a = b # Ok
      #\ WARNING Redeclared 'a' defined above without usage
      b = a # E
      #   └ WARNING Expected type 'B[int]', got 'A[object]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76814"])
    fun `supertypes of mixed variance covariant error`() = test("""
      class A[T]: # T is covariant
          def get(self) -> T: ...

      class B[S](A[S]): # S is invariant
          def set(self, s: S): ...
          def get(self) -> S: ...

      a : A[int] = A[int]()
      b : B[object] = B[object]()

      a = b # E
      #│  └ WARNING Expected type 'A[int]', got 'B[object]' instead
      #\ WARNING Redeclared 'a' defined above without usage
      b = a # E
      #   └ WARNING Expected type 'B[object]', got 'A[int]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76814"])
    fun `supertypes of mixed variance contravariant ok`() = test("""
      class A[T]: # T is contravariant
          def set(self, t: T): ...

      class B[S](A[S]): # S is invariant
          def set(self, s: S): ...
          def get(self) -> S: ...

      a : A[int] = A[int]()
      b : B[object] = B[object]()

      a = b # Ok
      #│  └ WARNING Expected type 'A[int]', got 'B[object]' instead FIXME # PY-89564
      #\ WARNING Redeclared 'a' defined above without usage
      b = a # E
      #   └ WARNING Expected type 'B[object]', got 'A[int]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76814"])
    fun `supertypes of mixed variance contravariant error`() = test("""
      class A[T]: # T is contravariant
          def set(self, t: T): ...

      class B[S](A[S]): # S is invariant
          def set(self, s: S): ...
          def get(self) -> S: ...

      a : A[object] = A[object]()
      b : B[int] = B[int]()

      a = b # E
      #│  └ WARNING FIXME Expected type 'A[object]', got 'B[int]' instead # PY-89564
      #\ WARNING Redeclared 'a' defined above without usage
      b = a # E
      #   └ WARNING Expected type 'B[int]', got 'A[object]' instead
      """)

    @Test
    @TestFor(issues = ["PY-79221"])
    fun `error when returning incompatible type due to variance`() = test("""
      data: list[int]

      def f() -> list[object]:
         return data # expect error
      #         ^^^^ WARNING Expected type 'list[object]', got 'list[int]' instead
      """)

    @Test
    @TestFor(issues = ["PY-79221"])
    fun `same type argument for type parameters with different variance`() = test("""
      from typing import Generic, TypeVar

      T_Co = TypeVar("T_Co", covariant=True)
      T_Contra = TypeVar("T_Contra", contravariant=True)

      class CoContra(Generic[T_Co, T_Contra]):
          pass

      def f[T1, T2](x: CoContra[T1, T2]):
          pass

      def g(x: CoContra[None, None]):
          f(x)
      """)
  }

  @Nested
  inner class Widening {

    @Test
    @TestFor(issues = ["PY-25989", "PY-84544"])
    fun `type var widening`() = test(
      TestOptions(assertRecursionPrevention = false),
      """
      from collections.abc import Iterable
      from typing import assert_type

      # PY-84544
      def foo(iterable: Iterable[int] | Iterable[str]) -> None:
          assert_type(next(iter(iterable)), int | str)


      # PY-25989
      assert_type(max(1, 2.6), float | int)
      assert_type(max(2.6, 1), float | int)
      max(1, object()) # WARNING Expected type 'int' (matched generic type 'SupportsRichComparisonT ≤: SupportsDunderLT[Any] | SupportsDunderGT[Any]'), got 'object' instead


      def bar[T: int, str](v1: T, v2: T) -> T:
          if (bool(input())):
              return v1
          return v2


      _ = bar(1, "a") # WARNING Expected type 'int' (matched generic type 'T ≤: int'), got 'Literal["a"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda parameter uses assignment context`() = test("""
      from typing import Callable

      _: Callable[[int], object] = lambda expr: expr
      #                                         └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda parameter uses assignment context split definition`() = test("""
      from typing import Callable

      a: Callable[[int], int]
      a = lambda expr: expr
      #                └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-28130"])
    fun `lambda parameter uses assignment context split definition class`() = test(
      TestOptions(assertRecursionPrevention = false),
      """
      from typing import Callable

      class C:
        attr: Callable[[int], str]
        def __init__(self):
          self.attr = lambda expr: str(expr)
      #                      └ TYPE int
      """)

    @Test
    fun `parameter with default widening`() {
      test("""
        from typing import Literal

        x: Literal[1] = 1

        def f(param=x): ...
        expr = f
        #└ TYPE (param: int) -> None
        """)
    }

    @Test
    fun `parameter with default literal widening to int`() = test("""
      def f(param=1): ...
      expr = f
      #└ TYPE (param: int) -> None
      """)

    @Test
    fun `parameter with explicit literal default keeps literal`() = test("""
      from typing import Literal

      x: Literal[1] = 1

      def f(param: Literal[1] = x): ...
      expr = f
      #└ TYPE (param: Literal[1]) -> None
      """)

    @Test
    fun `parameter with enum default widens to enum`() = test("""
      from enum import IntEnum

      class E(IntEnum):
        A = 1

      def f(param=E.A): ...
      expr = f
      #└ TYPE (param: E) -> None
      """)

    @Test
    @TestFor(issues = ["PY-87997"])
    fun `sentinel as default value for parameter`() = test("""
      SENTINEL = object()

      def f(a: int = SENTINEL):
          b = a
          expr = b
      #   └ TYPE int | SENTINEL
      """)

    @Test
    @TestFor(issues = ["PY-87997"])
    fun `sentinel assigned inside function`() = test("""
      SENTINEL = object()

      def f(a: int = SENTINEL):
          a = SENTINEL
          expr = a
      #   └ TYPE SENTINEL
      """)
  }

  @Nested
  inner class TypeNarrowingInAssignmentContexts {

    @Test
    fun `type narrowing is or equals`() = test("""
      from enum import Enum
      from typing import Literal

      class Color(Enum):
          R = 1
          G = 2
          B = 3
          RED = R
          GREEN = G
          BLUE = B

      def foo(v: Color | str) -> None:
          if v is Color.RED:
              r: Literal[Color.R] = v
          elif v == Color.G:
              g: Literal[Color.G] = v
          elif v is Color.B:
              b: Literal[Color.B] = v
          else:
              s: str = v
              c: Color = v # WARNING Expected type 'Color', got 'str' instead

          if v is Color.BLUE or isinstance(v, str):
              pass
          else:
              s: str = v # WARNING Expected type 'str', got 'Literal[Color.R, Color.G]' instead

      def bar(v: Literal[Color.R, "1"]) -> None:
          if isinstance(v, Color):
              r: Literal[Color.R] = v
          else:
              s: Literal["1"] = v
              c: Color = v # WARNING Expected type 'Color', got 'Literal["1"]' instead

      def buz(v: Color):
          if v is not Color.B and v != Color.RED:
              g: Literal[Color.G] = v
              s: str = v # WARNING Expected type 'str', got 'Literal[Color.G]' instead
      """)

    @Test
    @TestFor(issues = ["PY-79164"])
    fun `type narrowing in with string literals`() {
      test("""
        from typing import Literal

        def expects_bad_status(status: Literal["MALFORMED", "ABORTED"]): ...

        def expects_pending_status(status: Literal["PENDING"]): ...

        def parse_status(status: str) -> None:
            if status in ("MALFORMED", "ABORTED"):
                return expects_bad_status(status)

            if status == "PENDING":
                expects_pending_status(status)
        """)
    }

    @Test
    @TestFor(issues = ["PY-79164"])
    fun `type narrowing in with enum members`() = test("""
      from typing import Literal
      from enum import Enum

      class Color(Enum):
          R = 1
          G = 2
          B = 3
          RED = R
          BLUE = B

      def expects_red_or_blue(v: Literal[Color.RED, Color.B]): ...

      def expects_green(v: Literal[Color.G]): ...

      def foo(v: Color):
          if v in (Color.R, Color.BLUE):
              expects_red_or_blue(v)
          else:
              expects_green(v)

          if v not in (Color.R, Color.BLUE):
              expects_green(v)
          else:
              expects_red_or_blue(v)
      """)

    @Test
    @TestFor(issues = ["PY-79164"])
    fun `type narrowing in else branch keeps remaining members`() = test("""
      from enum import Enum

      class MyEnum(Enum):
          A = 1
          B = 2
          C = 3
          D = 4

      def foo(v: MyEnum):
          if v == MyEnum.A:
              pass
          elif v in (MyEnum.B, MyEnum.C):
              b_or_c: Literal[MyEnum.B, MyEnum.C] = v
      #               ^^^^^^^ ERROR Unresolved reference 'Literal'
              s: int = v # WARNING Expected type 'int', got 'Literal[MyEnum.B, MyEnum.C]' instead
          else:
              d: Literal[MyEnum.D] = v
      #          ^^^^^^^ ERROR Unresolved reference 'Literal'
              s: int = v # WARNING Expected type 'int', got 'Literal[MyEnum.D]' instead
      """)

    @Test
    fun `no warning if unreachable`() = test("""
      def foo() -> int:
          assert False
          return "42" # no warning here, because it is unreachable
      """)
  }

  @Nested
  inner class IntersectionTypes {

    @Test
    fun `intersection type assignability`() = test("""
      int_and_str: int & str
      #                └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
      str_and_int: int & str
      #                └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
      int_or_str: int | str

      n: int = int_and_str
      s: str = int_and_str

      int_and_str = n # WARNING Expected type 'int & str', got 'int' instead
      int_and_str = s
      #│            └ WARNING Expected type 'int & str', got 'str' instead
      #^^^^^^^^^^ WARNING Redeclared 'int_and_str' defined above without usage

      int_or_str = int_and_str
      int_and_str = int_or_str # WARNING Expected type 'int & str', got 'int | str' instead

      str_and_int = int_and_str
      int_and_str = str_and_int

      class A: pass
      class B: pass
      class C(A, B): pass

      a_and_b: A & B
      #          └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
      a_and_b = A() # WARNING Expected type 'A & B', got 'A' instead
      a_and_b = B()
      #│        ^^^ WARNING Expected type 'A & B', got 'B' instead
      #^^^^^^ WARNING Redeclared 'a_and_b' defined above without usage
      a_and_b = C()
      #^^^^^^ WARNING Redeclared 'a_and_b' defined above without usage

      a: A = a_and_b
      b: B = a_and_b
      c: C = a_and_b # WARNING Expected type 'C', got 'A & B' instead
      """)

    @Test
    fun `intersection type parsing`() = test("""
      expr: int & str
      #│        └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
      #└ TYPE int & str
      """)

    @Test
    fun `intersection type with partially unresolved operand`() = test("""
      expr: int & asdf
      #│        │ ^^^^ ERROR Unresolved reference 'asdf'
      #│        └ WARNING Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances
      #└ TYPE int & Unknown
      """)
  }

  @Nested
  inner class StdlibAssignability {

    @Test
    @TestFor(issues = ["PY-30747"])
    fun `pathlib Path matching os PathLike`() = test("""
      import pathlib
      import os

      def foo(p: pathlib.Path):
          with open(p) as file:
              pass

      p1: pathlib.Path
      p2: os.PathLike[bytes] = p1 # WARNING Expected type 'PathLike[bytes]', got 'Path' instead
      p3: os.PathLike[str] = p1
      """)

    @Test
    @TestFor(issues = ["PY-20769"])
    fun `PathLike passed to stdlib functions`() = test("""
      import os.path
      from pathlib import Path, PurePath


      # os.PathLike
      class A:
          def __fspath__(self) -> str:
              pass

      a = A()

      open(a)

      os.fspath(a)
      os.fsencode(a)
      os.fsdecode(a)

      Path(a)
      PurePath(a)

      os.path.abspath(a)


      # not os.PathLike
      class B:
          pass

      b = B()

      open(b) # WARNING Expected type 'int | str | bytes | PathLike[str] | PathLike[bytes]', got 'B' instead

      os.fspath(b) # WARNING No overload of 'fspath' matches the arguments. Argument types: (B). Expected one of: (path: str), (path: bytes), (path: PathLike[AnyStr])
      os.fsencode(b) # WARNING Expected type 'str | bytes | PathLike[str] | PathLike[bytes]', got 'B' instead
      os.fsdecode(b) # WARNING Expected type 'str | bytes | PathLike[str] | PathLike[bytes]', got 'B' instead

      Path(b) # WARNING Expected type 'str | PathLike[str]', got 'B' instead
      PurePath(b) # WARNING Expected type 'str | PathLike[str]', got 'B' instead

      os.path.abspath(b) # WARNING No overload of 'abspath' matches the arguments. Argument types: (B). Expected one of: (path: PathLike[AnyStr]), (path: AnyStr)


      # pathlib.PurePath
      p = Path(".")

      open(p)

      os.fspath(p)
      os.fsencode(p)
      os.fsdecode(p)

      Path(p)
      PurePath(p)

      os.path.abspath(p)
      """)

    @Test
    @TestFor(issues = ["PY-24287"])
    fun `promoting bytearray to bytes`() = test("""
      def f(bar: bytes):
          return bar


      f(bytearray())
      """)

    @Test
    @TestFor(issues = ["PY-22769"])
    fun `replace called on union of str and bytes with str arguments`() = test("""
      from typing import Union


      def foo(path: Union[bytes, str]) -> None:
          path.replace("/", "\\")
      """)

    @Test
    @TestFor(issues = ["PY-18275"])
    fun `str format`() = test("""
      '{}'.format(0)
      """)

    @Test
    @TestFor(issues = ["PY-10660"])
    fun `struct unpack`() = test("""
      from struct import Struct

      s = Struct('c')
      s.unpack(' ') # WARNING Expected type 'Buffer', got 'Literal[" "]' instead
      s.unpack(b' ')
      """)

    @Test
    @TestFor(issues = ["PY-19796"])
    fun `ord`() = test("""
      ord(b'A')
      ord('A')
      """)

    @Test
    @TestFor(issues = ["PY-21350"])
    fun `builtin input`() = test("""
      class A:
          pass

      input(A())
      input(b"b")
      input(u"u")
      """)

    @Test
    fun `builtin operators and numerics`() = test("""
      def test_operators():
          print(2 + 'foo') # WARNING Expected type 'int', got 'Literal["foo"]' instead
          print(b'foo' + 'bar') # WARNING Expected type 'Buffer', got 'Literal["bar"]' instead
          print(b'foo' + 3) # WARNING Expected type 'Buffer', got 'Literal[3]' instead


      def test_numerics():
          abs(False)
          int(10)
          long(False)
      #   ^^^^ ERROR Unresolved reference 'long'
          float(False)
          complex(False)
          divmod(False, False)
          divmod(b'foo', 'bar') # WARNING No overload of 'divmod' matches the arguments. Argument types: (bytes, Literal["bar"]). Expected one of: (x: SupportsDivMod[_T_contra, _T_co], y: str), (x: bytes, y: SupportsRDivMod[bytes, _T_co])
          pow(False, True)
          round(False, 'foo') # WARNING No overload of 'round' matches the arguments. Argument types: (Literal[False], Literal["foo"]). Expected one of: (number: _SupportsRound1[int], ndigits: None), (number: _SupportsRound2[int], ndigits: SupportsIndex)
      """)

    @Test
    @TestFor(issues = ["PY-23289", "PY-23391", "PY-24194", "PY-24789"])
    fun `typing Supports protocols`() = test("""
      import typing


      def check_complex(p: typing.SupportsComplex):
          print(p.__complex__())


      class A:
          def __int__(self):
              return 5


          def __float__(self):
              return 5.0


          def __complex__(self):
              return complex(5.0, 0.0)

          def __bytes__(self):
              return b'bytes'

          def __abs__(self):
              return 5

          def __round__(self, n=None):
              return 5


      a = A()
      print(int(a))
      print(float(a))
      check_complex(a)
      print(bytes(a))
      print(abs(a))
      print(round(a))
      """)
  }

  @Nested
  inner class Annotated {

    @Test
    @TestFor(issues = ["PY-41847"])
    fun `typing Annotated type`() = test("""
      from typing import Annotated
      A = Annotated[bool, 'Some constraint']
      a: A = 'str' # WARNING Expected type 'bool', got 'Literal["str"]' instead
      b: A = True
      c: Annotated[bool, 'Some constraint'] = 'str' # WARNING Expected type 'bool', got 'Literal["str"]' instead
      d: Annotated[str, 'Some constraint'] = 'str'
      """)

    @Test
    @TestFor(issues = ["PY-41847"])
    fun `typing Annotated type from another file`() = test(
      """
      from annotated import A


      a: A = 'str' # WARNING Expected type 'int', got 'Literal["str"]' instead
      a1: A = 42
      """,
      "annotated.py" to """
        from typing import Annotated


        A = Annotated[int, "Some constraint"]
        """,
    )
  }

  @Nested
  inner class ClassObjectsAndType {

    @Test
    fun `class object type compatibility`() = test("""
      from typing import Type, TypeVar, Optional

      class MyClass:
          pass

      def expects_myclass(x: Type[MyClass]):
          pass

      expects_myclass(MyClass()) # WARNING Expected type 'type[MyClass]', got 'MyClass' instead
      expects_class(MyClass)
      #^^^^^^^^^^^^ ERROR Unresolved reference 'expects_class'

      T1 = TypeVar('T1')
      def expects_any_class(x: Type[T1]):
          pass

      expects_any_class(MyClass)
      expects_any_class(MyClass()) # WARNING Expected type 'type[T1]', got 'MyClass' instead
      expects_any_class(object)
      expects_any_class(object()) # WARNING Expected type 'type[T1]', got 'object' instead

      T2 = TypeVar('T2', MyClass)
      #    ^^^^^^^^^^^^^^^^^^^^^^ ERROR A single constraint is not allowed
      def expects_myclass_descendant(x: Type[T2]):
          pass

      expects_myclass_descendant(MyClass)
      expects_myclass_descendant(MyClass()) # WARNING Expected type 'type[T2 ≤: MyClass]', got 'MyClass' instead
      expects_myclass_descendant(object) # WARNING Expected type 'type[T2 ≤: MyClass]', got 'type[object]' instead
      expects_myclass_descendant(object()) # WARNING Expected type 'type[T2 ≤: MyClass]', got 'object' instead

      def expects_myclass_descendant_or_none(x: Optional[Type[T2]]):
          pass

      expects_myclass_descendant_or_none(MyClass)
      expects_myclass_descendant_or_none(MyClass()) # WARNING Expected type 'type[T2 ≤: MyClass] | None', got 'MyClass' instead
      expects_myclass_descendant_or_none(object) # WARNING Expected type 'type[T2 ≤: MyClass] | None', got 'type[object]' instead
      expects_myclass_descendant_or_none(object()) # WARNING Expected type 'type[T2 ≤: MyClass] | None', got 'object' instead
      """)

    @Test
    @TestFor(issues = ["PY-42418"])
    fun `parametrized builtin collections and their typing aliases are equivalent`() = test("""
      from typing import Dict, FrozenSet, List, Set, Tuple


      def expects_builtin_list(xs: list[int]):
          expects_typing_List(xs)
          expects_typing_List(['a']) # WARNING Expected type 'list[int]', got 'list[Literal["a"]]' instead


      def expects_typing_List(xs: List[int]):
          expects_builtin_list(xs)
          expects_builtin_list(['a']) # WARNING Expected type 'list[int]', got 'list[Literal["a"]]' instead


      def expects_builtin_set(xs: set[int]):
          expects_typing_Set(xs)
          expects_typing_Set({'a'}) # WARNING Expected type 'set[int]', got 'set[Literal["a"]]' instead


      def expects_typing_Set(xs: Set[int]):
          expects_builtin_set(xs)
          expects_builtin_set({'a'}) # WARNING Expected type 'set[int]', got 'set[Literal["a"]]' instead


      def expects_builtin_frozenset(xs: frozenset[int]):
          expects_typing_FrozenSet(xs)
          expects_typing_FrozenSet(frozenset(['a'])) # WARNING Expected type 'frozenset[int]', got 'frozenset[str]' instead


      def expects_typing_FrozenSet(xs: FrozenSet[int]):
          expects_builtin_frozenset(xs)
          expects_builtin_frozenset(frozenset(['a'])) # WARNING Expected type 'frozenset[int]', got 'frozenset[str]' instead


      def expects_builtin_dict(xs: dict[str, int]):
          expects_typing_Dict(xs)
          expects_typing_Dict({42: 'a'}) # WARNING Expected type 'dict[str, int]', got 'dict[Literal[42], Literal["a"]]' instead


      def expects_typing_Dict(xs: Dict[str, int]):
          expects_builtin_dict(xs)
          expects_builtin_dict({42: 'a'}) # WARNING Expected type 'dict[str, int]', got 'dict[Literal[42], Literal["a"]]' instead


      def expects_builtin_tuple(xs: tuple[str, int]):
          expects_typing_Tuple(xs)
          expects_typing_Tuple((42, 'a')) # WARNING Expected type 'tuple[str, int]', got 'tuple[Literal[42], Literal["a"]]' instead


      def expects_typing_Tuple(xs: Tuple[str, int]):
          expects_builtin_tuple(xs)
          expects_builtin_tuple((42, 'a')) # WARNING Expected type 'tuple[str, int]', got 'tuple[Literal[42], Literal["a"]]' instead
      """)

    @Test
    @TestFor(issues = ["PY-42418"])
    fun `parametrized builtin type and typing Type are equivalent`() = test("""
      from typing import Type, TypeVar


      def expects_typing_Type(x: Type[str]):
          expects_builtin_type(x)
          expects_builtin_type(int) # WARNING Expected type 'type[str]', got 'type[int]' instead


      def expects_builtin_type(x: type[str]):
          expects_typing_Type(x)
          expects_typing_Type(int) # WARNING Expected type 'type[str]', got 'type[int]' instead


      T = TypeVar('T', bound=str)


      def expects_generic_builtin_type(x: type[T]):
          expects_generic_typing_Type(x)
          expects_generic_typing_Type(int) # WARNING Expected type 'type[T ≤: str]', got 'type[int]' instead


      def expects_generic_typing_Type(x: Type[T]):
          expects_generic_builtin_type(x)
          expects_generic_builtin_type(int) # WARNING Expected type 'type[T ≤: str]', got 'type[int]' instead
      """)

    @Test
    @TestFor(issues = ["PY-23053"])
    fun `unbound type vars match class object types`() = test("""
      from typing import TypeVar

      T = TypeVar('T')
      B = TypeVar('B', str)
      #   ^^^^^^^^^^^^^^^^^ ERROR A single constraint is not allowed


      def f1(p: T):
          return p


      f1(str)


      def f2(p: B):
          return p


      f2(str) # WARNING Expected type 'B ≤: str', got 'type[str]' instead


      def g1(p):
          '''
          :type p: T 
          '''


      g1(str)


      def g2(p):
          '''
          :type p: T <= str 
          '''


      g2(str) # WARNING Expected type 'T ≤: str', got 'type[str]' instead

      xs = list([str])
      """)
  }

  @Nested
  inner class GenericCallMatching {

    @Test
    fun `class constructor type parameter defined on inheritance`() = test("""
      from typing import Generic, TypeVar

      T = TypeVar('T')

      class Box(Generic[T]):
          def __init__(self, value: T) -> None:
              pass

      class StrBox(Box[str]):
          pass

      StrBox(42) # WARNING Expected type 'str' (matched generic type 'T'), got 'Literal[42]' instead
      """)

    @Test
    fun `explicitly parameterized generic constructor call`() = test("""
      class A[T]:
          def __init__(self, v: T) -> None: ...

      A[int]("") # WARNING Expected type 'int' (matched generic type 'T'), got 'Literal[""]' instead
      """)

    @Test
    @TestFor(issues = ["PY-28127", "PY-31424"])
    fun `initializing type var`() = test("""
      from typing import TypeVar

      TypeVar("T", int, str, bound=int, covariant=True, contravariant=True)
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Bivariant type variables are not supported
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Constraints cannot be combined with bound=…
      TypeVar("T", int, str, bound='int', covariant=True, contravariant=True)
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Bivariant type variables are not supported
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Constraints cannot be combined with bound=…
      TypeVar("T", int, 'str', bound=int, covariant=True, contravariant=True)
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Bivariant type variables are not supported
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Constraints cannot be combined with bound=…
      TypeVar("T", 'int', 'str', bound=int, covariant=True, contravariant=True)
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Bivariant type variables are not supported
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Constraints cannot be combined with bound=…
      TypeVar("T", 0, 1, bound=2, covariant=3, contravariant=4)
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Bivariant type variables are not supported
      #^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Constraints cannot be combined with bound=…
      """)

    @Test
    @TestFor(issues = ["PY-22513"])
    fun `generic kwargs`() = test("""
      from typing import Any, TypeVar


      T = TypeVar('T')


      def generic_kwargs(**kwargs: T) -> None:
          pass


      generic_kwargs(a=1, b='foo')
      """)

    @Test
    @TestFor(issues = ["PY-22730"])
    fun `optional of bound type var in warnings`() = test("""
      from typing import Optional, TypeVar


      T = TypeVar('T', int)
      #   ^^^^^^^^^^^^^^^^^ ERROR A single constraint is not allowed


      def expects_int_subclass_or_none(x: Optional[T]):
          pass


      expects_int_subclass_or_none('foo') # WARNING Expected type 'T ≤: int | None', got 'Literal["foo"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-16855"])
    fun `typing TypeVar with unresolved bound`() = test("""
      from typing import TypeVar


      T = TypeVar('T', int, unresolved)
      #                     ^^^^^^^^^^ ERROR Unresolved reference 'unresolved'


      def calc(a: T, b: T):
          pass


      calc('a', 0) # OK: 'unresolved' is treated as 'Any'
      """)

    @Test
    @TestFor(issues = ["PY-25994"])
    fun `unresolved receiver generic`() = test("""
      from typing import TypeVar, Dict, Iterable, Any

      T = TypeVar("T")


      def foo(values: Dict[T, Iterable[Any]]):
          for e in []:
              values.setdefault(e, undefined)
      #                            ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      """)

    @Test
    fun `chained comparisons generic matching`() = test("""
      from typing import Generic, TypeVar

      T = TypeVar('T')


      class MyClass(Generic[T]):
          def __init__(self, x: T):
              pass

          def __lt__(self, other: 'MyClass[T]'):
              pass


      x = MyClass(1) < MyClass(2) < MyClass('foo') # WARNING Expected type 'MyClass[int]' (matched generic type 'MyClass[T]'), got 'MyClass[str]' instead
      """)

    @Test
    fun `class inherits Generic to order type parameters`() = test("""
      from typing import Generic, TypeVar

      T1 = TypeVar('T1')
      T2 = TypeVar('T2')

      class Box(Generic[T1]):
          def get(self) -> T1:
              pass

      class Pair(Box[T2], Generic[T1, T2]):
          pass

      xs: Pair[int, str] = ...
      #                    ^^^ WARNING Expected type 'Pair[int, str]', got 'EllipsisType' instead
      expr = xs.get()
      #└ TYPE str
      """)

    @Test
    fun `function return type checks`() = test(
      TestOptions(enablePyAnyType = false, assertRecursionPrevention = false),
      """
      from typing import List, Optional, Union, Generator, Iterable

      def a(x: List[int]) -> List[str]:
          return [x] # WARNING Expected type 'list[str]', got 'list[list[int]]' instead

      def b(x: int) -> List[str]:
          return [1,2] # WARNING Expected type 'list[str]', got 'list[Literal[1, 2]]' instead

      def c() -> int:
          return 'abc' # WARNING Expected type 'int', got 'Literal["abc"]' instead

      def d(x: int) -> List[str]:
          return [str(x)]

      def e() -> int:
          pass

      def f() -> Optional[str]:
          x = int(input())
          if x > 0:
              return 42 # WARNING Expected type 'str | None', got 'Literal[42]' instead
          elif x == 0:
              return 'abc'
          else:
              return

      def g(x) -> int:
          if x:
              return 'abc' # WARNING Expected type 'int', got 'Literal["abc"]' instead
          else:
              return {} # WARNING Expected type 'int', got 'dict[Any, Any]' instead

      def h(x) -> int:
          return # WARNING Expected type 'int', got 'None' instead

      def i() -> Union[int, str]:
          pass

      def j(x) -> Union[int, str]: # WARNING Expected type 'int | str', got 'None' instead
          x = 42

      def k() -> None:
          if True:
              pass

      def l(x) -> int: # WARNING Expected type 'int', got 'Literal[42] | None' instead
          if x == 1:
              return 42

      def m(x) -> None:
          '''Does not display warning about implicit return, because annotated '-> None' '''
          if x:
              return

      def n() -> Generator[int, Any, str]:
      #                         ^^^ ERROR Unresolved reference 'Any'
          yield 13
          return 42 # WARNING Expected type 'str', got 'Literal[42]' instead

      def o(val) -> int:
          assert val is int
          return val

      def t() -> Iterable[int]:
          yield 13
          return "str" # no warning here
      """)

    @Test
    fun `ellipsis in function with specified return type`() = test("""
      def bar() -> int:
          ...
      """)

    @Test
    @TestFor(issues = ["PY-25045"])
    fun `union of int and float should be considered as dividable`() = test("""
      from typing import Union


      def foo(x):
          return x / (60 * 60)

      bar = 0  # type: Union[int, float]
      foo(bar)
      """)

    @Test
    fun `matching open function call types`() = test(
      """
      from foo import calcT, calcB

      with open('1.txt') as file1:
          calcT(file1)
          calcB(file1) # WARNING Expected type 'BinaryIO', got 'TextIOWrapper[_WrappedBuffer]' instead

      with open('1.txt', 'rb') as file2:
          calcT(file2) # WARNING Expected type 'TextIO', got 'BufferedReader[_BufferedReaderStream]' instead
          calcB(file2)
      """,
      "foo.py" to """
        def calcT(a): pass
        def calcB(a): pass
        """,
      "foo.pyi" to """
        from typing import BinaryIO, TextIO


        def calcT(a: TextIO) -> int: ...
        def calcB(a: BinaryIO) -> int: ...
        """,
    )

    @Test
    fun `type var bound to LiteralString`() = test("""
      from typing import TypeVar, LiteralString
      TLiteral = TypeVar("TLiteral", bound=LiteralString)
      def literal_identity(s: TLiteral) -> TLiteral:
          return s
      s: LiteralString
      y2 = literal_identity(s)
      """)

    @Test
    fun `flag name`() = test("""
      from enum import IntFlag


      def test_int_flag(x: IntFlag) -> str | None:
          return x.name
      """)
  }

  @Nested
  inner class ContextManagersAndCast {

    @Test
    @TestFor(issues = ["PY-9289"])
    fun `with open binary`() = test("""
      def f1(arg: str):
          pass
      def f2(arg: bytes):
          pass
      with open('file.txt', 'rb') as f:
          contents = f.read()
          f1(contents) # WARNING Expected type 'str', got 'bytes' instead
          f2(contents)
      """)

    @Test
    @TestFor(issues = ["PY-72232"])
    fun `with item non context manager`() = test("""
      class A:
          pass

      with A() as a: # WARNING Expected type 'contextlib.AbstractContextManager', got 'A' instead
          pass
      """)

    @Test
    fun `cast result type`() = test("""
      from typing import cast

      def foo(x):
          expr = cast(str, x)
      #   └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-82500"])
    fun `ordinary subscription expression cannot be used as type hint`() = test("""
      xs: list[type[str]]
      expr: xs[0]
      #└ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-82500"])
    fun `ordinary binary expression cannot be used as type hint`() = test("""
      class B:
          def __add__(self, item: int) -> type[str]:
              ...
      x: B
      expr: x + 1
      #│    ^^^^^ WARNING Invalid type annotation
      #└ TYPE Unknown
      """)
  }

  @Nested
  inner class TypeIsAndMapArgumentMatching {

    @Test
    fun `TypeIs result assignable to bool parameter`() = test("""
      from typing import Any, Callable
      from typing_extensions import TypeIs

      def foo(c: bool):
         ...

      def is_int(x: Any) -> TypeIs[int]:
         ...

      foo(is_int(1))
      """)

    @Test
    fun `TypeIs result assignable to int parameter`() = test("""
      from typing import Any, Callable
      from typing_extensions import TypeIs

      def foo(c: int):
         ...

      def is_int(x: Any) -> TypeIs[int]:
         ...

      foo(is_int(1))
      """)

    @Test
    fun `TypeIs result not assignable to str parameter`() = test("""
      from typing import Any, Callable
      from typing_extensions import TypeIs

      def foo(c: str):
         ...

      def is_int(x: Any) -> TypeIs[int]:
         ...

      foo(is_int(1)) # WARNING Expected type 'str', got 'TypeIs[int]' instead
      """)

    @Test
    @TestFor(issues = ["PY-20073"])
    fun `map arguments in opposite order`() = test(
      TestOptions(assertRecursionPrevention = false),
      """
      map('foo', lambda c: 42)
      #   │      ^^^^^^^^^^^^ WARNING Expected type 'Iterable[_T1]', got '(c: Unknown) -> Literal[42]' instead
      #   ^^^^^ WARNING Expected type '(_T1) -> Unknown' (matched generic type '(_T1) -> _S'), got 'Literal["foo"]' instead
      """,
    )
  }

  @Nested
  inner class SimpleFunctionAssignment {

    @Test
    @TestFor(issues = ["PY-28076"])
    fun `assignment parens`() = test("""
      ((expr)) = 42
      #└ TYPE Literal[42]
      """)

    @Test
    fun `function assignment transitivity`() = test("""
      def f():
          return 1
      g = f
      h = g
      expr = h()
      #└ TYPE Literal[1]
      """)
  }
}
