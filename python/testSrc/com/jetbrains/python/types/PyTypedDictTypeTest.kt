// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for [TypedDict][https://docs.python.org/3/library/typing.html#typing.TypedDict]:
 * definition forms, subscription, required/optional/`ReadOnly` keys, `total=`, the alternative call
 * syntax, `NotRequired`/`Required`, `extra_items`, `Unpack[...]` kwargs, and related inspections.
 */
class PyTypedDictTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class InferredTypeOfATypedDictExpression {
    @Test
    fun `TypedDict instance type`() = test("""
      from typing import TypedDict
      class A(TypedDict):
          x: int
      a: A = {'x': 42}
      expr = a
      # └ TYPE A
      """)

    @Test
    @TestFor(issues = ["PY-36008"])
    fun `TypedDict alternative syntax yields a type`() = test("""
      from typing import TypedDict
      A = TypedDict('A', {'x': int}, total=False)
      expr = A
      # └ TYPE type[A]
      """)
  }

  @Nested
  inner class SubscriptionAndGet {
    @Test
    @TestFor(issues = ["PY-36008"])
    fun `subscription with string literal key`() = test("""
      from typing import TypedDict
      class A(TypedDict):
          x: int
      a: A = {'x': 42}
      expr = a['x']
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-36008"])
    fun `subscription with single Literal key parameter`() = test("""
      from typing import Literal, TypedDict
      class TD(TypedDict):
          a: int
          b: str
      def foo(v: TD, k: Literal['b']):
          expr = v[k]
      #   └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-36008"])
    fun `subscription with multiple Literal key parameter`() = test("""
      from typing import Literal, TypedDict
      class TD(TypedDict):
          a: int
          b: str
          c: bool
      def foo(v: TD, k: Literal['c', 'b']):
          expr = v[k]
      #   └ TYPE bool | str
      """)

    @Test
    @TestFor(issues = ["PY-36008"])
    fun `subscription with undefined key`() = test("""
      from typing import TypedDict
      class A(TypedDict):
          x: int
      a: A = {'x': 42}
      expr = a[x]
      #│       └ ERROR Unresolved reference 'x'
      #└ TYPE Unknown FIXME Any
      """)

    @Test
    @TestFor(issues = ["PY-36008"])
    fun `get of required key`() = test("""
      from typing import TypedDict
      class A(TypedDict):
          x: int
      a: A = {'x': 42}
      expr = a.get('x')
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-36008"])
    fun `get of optional key`() = test("""
      from typing import TypedDict
      class A(TypedDict, total=False):
          x: int
      a: A = {'x': 42}
      expr = a.get('x')
      #└ TYPE int | None
      """)

    @Test
    @TestFor(issues = ["PY-36008"])
    fun `get with default of the same value type`() = test("""
      from typing import TypedDict
      class A(TypedDict, total=False):
          x: int
      a: A = {'x': 42}
      expr = a.get('x', 42)
      #└ TYPE int | Literal[42]
      """)

    @Test
    @TestFor(issues = ["PY-36008"])
    fun `get with default of a different value type`() = test("""
      from typing import TypedDict
      class A(TypedDict, total=False):
          x: int
      a: A = {'x': 42}
      expr = a.get('x', '')
      #└ TYPE int | Literal[""]
      """)
  }

  @Nested
  inner class ReadOnlyRequiredAnnotatedItemTypes {
    @Test
    @TestFor(issues = ["PY-77796"])
    fun `ReadOnly item type`() = test("""
      from typing import TypedDict, ReadOnly
      class A(TypedDict):
          x: ReadOnly[str]
      def f(a: A):
          expr = a['x']
      #   └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-77796"])
    fun `Required ReadOnly item type`() = test("""
      from typing import TypedDict, Required, ReadOnly
      class A(TypedDict):
          x: Required[ReadOnly[int]]
      def f(a: A):
          expr = a['x']
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-77796"])
    fun `Required Annotated ReadOnly item type`() = test("""
      from typing import TypedDict, Required, Annotated, ReadOnly
      class A(TypedDict):
          x: Required[Annotated[ReadOnly[str], 1]]
      def f(a: A):
          expr = a['x']
      #   └ TYPE str
      """)
  }

  @Nested
  inner class UnpackKwargs {
    @Test
    @TestFor(issues = ["PY-55044"])
    fun `kwargs typed with Unpack of TypedDict`() = test("""
      from typing import TypedDict, Unpack
      class Movie(TypedDict):
          name: str
          year: int
      def foo(**x: Unpack[Movie]):
          expr = x
      #   └ TYPE Movie
      """)
  }

  @Nested
  inner class ExtraItemsInferredTypes {
    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items known key type`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON313),
      """
      from typing_extensions import TypedDict
      
      class Movie(TypedDict, extra_items=int):
          name: str
      
      def movie_keys(movie: Movie) -> None:
          expr = movie["name"]
      #   └ TYPE str
      """,
    )

    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items arbitrary key type`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON313),
      """
      from typing_extensions import TypedDict
      
      class Movie(TypedDict, extra_items=int):
          name: str
      
      def movie_keys(movie: Movie) -> None:
          expr = movie["novel_adaptation"]
      #   └ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items multiple arbitrary keys`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON313),
      """
      from typing_extensions import TypedDict
      
      class Movie(TypedDict, extra_items=int):
          name: str
      
      def movie_keys(movie: Movie) -> None:
          expr = movie["year"]
      #   └ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items reflected in items`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON313, assertRecursionPrevention = false),
      """
      from typing_extensions import TypedDict

      class MovieExtraInt(TypedDict, extra_items=int):
          name: str

      def foo(movie: MovieExtraInt) -> None:
          expr = list(movie.items())
      #   └ TYPE list[tuple[str, str | int]]
      """,
    )

    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items reflected in values`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON313, assertRecursionPrevention = false),
      """
      from typing_extensions import TypedDict

      class MovieExtraInt(TypedDict, extra_items=int):
          name: str

      def foo(movie: MovieExtraInt) -> None:
          expr = list(movie.values())
      #   └ TYPE list[str | int]
      """,
    )

    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items reflected in popitem`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON313),
      """
      from typing_extensions import TypedDict
      
      class MovieExtraInt(TypedDict, extra_items=int):
          name: str
      
      def foo(movie: MovieExtraInt) -> None:
          expr = movie.popitem()
      #   │            ^^^^^^^ WARNING This operation might break TypedDict consistency
      #   └ TYPE tuple[str, str | int]
      """,
    )
  }

  @Nested
  inner class InspectionsRequiredOptionalReadonlyKeys {
    @Test
    @TestFor(issues = ["PY-46661"])
    fun `missing and extra keys in return type`() = test("""
      from typing import TypedDict, List, Optional, Union
      
      
      class Point(TypedDict):
          x: int
          y: int
      
      
      def a(x: List[int]) -> Point:
          return [x]
      #          ^^^ WARNING Expected type 'Point', got 'list[list[int]]' instead
      
      def b(x: int) -> Point:
          return {'x': 42}
      #          ^^^^^^^^^ WARNING TypedDict 'Point' has missing key: 'y'
      
      def c() -> Point:
          return {'x': 'abc', 'y': 42}
      #                ^^^^^ WARNING Expected type 'int', got 'str' instead
      
      def d() -> Point:
          return {'x': 42, 'y': 42, 'k': 42}
      #                             ^^^^^^^ WARNING Extra key 'k' for TypedDict 'Point'
      
      def e1(x: int):
          return {'x': x}
      
      def e(x: int) -> Point:
          return e1(x)
      #          ^^^^^ WARNING Expected type 'Point', got 'dict[str, int]' instead
      
      def f1(x: int) -> Point:
          pass
      
      def f(x: str) -> Point:
          return f1(int(x))
      
      def g() -> Point:
          x = int(input())
          y = {'x': x}
          if x > 0:
              return y
      #              └ WARNING Expected type 'Point', got 'dict[str, int]' instead
          elif x == 0:
              return Point(x=442, y=42)
          else:
              return
      #       ^^^^^^ WARNING Expected type 'Point', got 'None' instead
      
      def h(x) -> Point:
      #           ^^^^^ WARNING Expected type 'Point', got 'None' instead
          x = 42
      
      def i() -> Point:
      #          ^^^^^ WARNING Expected type 'Point', got 'None' instead
          if True:
              pass
      """)

    @Test
    @TestFor(issues = ["PY-53611"])
    fun `Required and NotRequired keys`() = test("""
      from typing import TypedDict
      from typing_extensions import Required, NotRequired
      class WithTotalFalse(TypedDict, total=False):
          x: Required[int]
      class WithTotalTrue(TypedDict, total=True):
          x: NotRequired[int]
      class WithoutTotal(TypedDict):
          x: NotRequired[int]
      class WithoutTotalWithExplicitRequired(TypedDict):
          x: Required[int]
          y: NotRequired[int]
      AlternativeSyntax = TypedDict("AlternativeSyntax", {'x': NotRequired[int]})
      with_total_false: WithTotalFalse = {}
      #                                  ^^ WARNING TypedDict 'WithTotalFalse' has missing key: 'x'
      with_total_true: WithTotalTrue = {}
      without_total: WithoutTotal = {}
      without_total_with_explicit_required: WithoutTotalWithExplicitRequired = {}
      #                                                                        ^^ WARNING TypedDict 'WithoutTotalWithExplicitRequired' has missing key: 'x'
      alternative_syntax: AlternativeSyntax = {}
      """)

    @Test
    @TestFor(issues = ["PY-53611"])
    fun `Required and NotRequired equivalence`() = test("""
      from typing_extensions import TypedDict, Required, NotRequired
      
      
      class _MovieBase0(TypedDict):
          title: str
      
      
      class Movie0(_MovieBase0, total=False):
          year: int
      
      
      class Movie1(TypedDict):
          title: Required[str]
          year: NotRequired[int]
      
      
      class Movie2(TypedDict):
          title: NotRequired[str]
          year: NotRequired[int]
      
      
      def f(movie: Movie0):
          ...
      
      
      f(Movie1(title="Jaws"))
      f(Movie2(title="Jaws"))
      # ^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'Movie0', got 'Movie2' instead
      """)

    @Test
    @TestFor(issues = ["PY-53611"])
    fun `Required and NotRequired mixed with Annotated`() = test("""
      from typing_extensions import TypedDict, Required, NotRequired, Annotated
      class A(TypedDict):
          x: Annotated[NotRequired[int], 'Some constraint']
      def f(a: A):
          pass
      f({})
      class B(TypedDict, total=False):
          x: Annotated[Required[int], 'Some constraint']
      def g(b: B):
          pass
      g({})
      # ^^ WARNING TypedDict 'B' has missing key: 'x'
      """)

    @Test
    @TestFor(issues = ["PY-53611"])
    fun `Required type specifications across files`() = test(
      """
      from required import A, AlternativeSyntax
      
      
      a: A = {}
      #      ^^ WARNING TypedDict 'A' has missing keys: 'x', 'y'
      a1: A = {'x': 42, 'y': 42}
      a2: AlternativeSyntax = {'y': "str"}
      #                             ^^^^^ WARNING Expected type 'int', got 'str' instead
      """,
      "required.py" to """
        from typing_extensions import TypedDict, Annotated, Required, NotRequired
        
        
        class A(TypedDict, total=False):
            x: Required[int]
            y: Annotated[Required[int], 'Some constraint']
        
        
        AlternativeSyntax = TypedDict("AlternativeSyntax", {'x': NotRequired[int], 'y': Required[Annotated[int, 'Some constraint']]})
        """,
    )

    @Test
    fun `Required with ReadOnly`() = test("""
      from typing_extensions import TypedDict, Required, NotRequired, ReadOnly
      
      class Movie(TypedDict):
          name: ReadOnly[Required[str]]
          year: NotRequired[int]
      
      m: Movie = {"year": 2024}
      #          ^^^^^^^^^^^^^^ WARNING TypedDict 'Movie' has missing key: 'name'
      """)

    @Test
    fun `ReadOnly consistency in assignability`() = test("""
      from typing import TypedDict, Required, NotRequired, ReadOnly
      
      class A1(TypedDict):
          x: NotRequired[str]
      
      class B1(TypedDict):
          x: NotRequired[ReadOnly[str]]
      
      class B2(TypedDict):
          x: ReadOnly[NotRequired[str]]
      
      class C(TypedDict):
          x: Required[str]
      
      def func1(b1: B1, b2: B2, c: C):
          v1: A1 = b1
      #            ^^ WARNING Expected type 'A1', got 'B1' instead
          v2: A1 = b2
      #            ^^ WARNING Expected type 'A1', got 'B2' instead
          v3: B1 = c
      
      class A2(TypedDict):
          x: ReadOnly[NotRequired[object]]
      
      class B3(TypedDict):
          pass
      
      def func2(b: B3):
          a: A2 = b
      """)
  }

  @Nested
  inner class InspectionsDictLiteralKeysAndValues {
    @Test
    @TestFor(issues = ["PY-78126"])
    fun `variable key in dict literal`() = test(TestOptions(enablePyAnyType = false), """
      from typing import TypedDict, Literal
      class Movie(TypedDict):
          name: str
          year: int
      def foo(key: str):
          m: Movie = {key: "abb", "year": 1917}
      #              ^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'Movie', got 'dict[str, str | int]' instead
      def bar(key: Literal["name"]):
          m: Movie = {key: "abb", "year": 1917} # OK
      def buz(key: Literal["wrong_key"]):
          m: Movie = {key: "abb", "year": 1917}
      #              │^^^^^^^^^^ WARNING Extra key 'wrong_key' for TypedDict 'Movie'
      #              ^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING TypedDict 'Movie' has missing key: 'name'
      """)

    @Test
    @TestFor(issues = ["PY-38873"])
    fun `value access through list field`() = test("""
      from typing import TypedDict, List, LiteralString
      Movie = TypedDict('Movie', {'address': List[str]}, total=False)
      class Movie2(TypedDict, total=False):
          address: List[str]
      movie = Movie()
      movie2 = Movie2()
      s1: LiteralString = movie['address'][0]
      #                   ^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'LiteralString', got 'str' instead
      s2: LiteralString = movie2['address'][0]
      #                   ^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'LiteralString', got 'str' instead
      """)

    @Test
    @TestFor(issues = ["PY-79733"])
    fun `TypedDict type inferred for comprehensions`() = test("""
      from typing import TypedDict


      class Foo(TypedDict):
          foo: str


      foo: Foo = {"foo": "bar"}
      foo_list1: list[Foo] = [{"foo": bar} for bar in ["bar"]]
      foo_list2: list[Foo] = [{"foo": bar, "buz": "qux"} for bar in ["bar"]]
      #                      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'list[Foo]', got 'list[dict[Literal["foo", "buz"], str | Literal["qux"]]]' instead
      foo_set1: set[Foo] = {{"foo": bar} for bar in ["bar"]}
      foo_set2: set[Foo] = {{"foo": bar, "buz": "qux"} for bar in ["bar"]}
      #                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'set[Foo]', got 'set[dict[Literal["foo", "buz"], str | Literal["qux"]]]' instead
      foo_dict1: dict[str, Foo] = {bar: {"foo": bar} for bar in ["bar"]}
      foo_dict2: dict[str, Foo] = {bar: {"foo": bar, "buz": "qux"} for bar in ["bar"]}
      #                           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'dict[str, Foo]', got 'dict[str, dict[Literal["foo", "buz"], str | Literal["qux"]]]' instead
      """)
  }

  @Nested
  inner class InspectionsExtraItems {
    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items in dict literal`() = test("""
      from typing_extensions import TypedDict

      class MovieNoExtra(TypedDict):
          name: str

      a: MovieNoExtra = {"name": "Blade Runner", "novel_adaptation": True}
      #                                          ^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Extra key 'novel_adaptation' for TypedDict 'MovieNoExtra'

      class Movie(TypedDict, extra_items=bool):
          name: str

      b: Movie = {"name": "Blade Runner", "novel_adaptation": True}

      c: Movie = {"name": "Blade Runner", "novel_adaptation": True, "is_classic": False}

      d: Movie = {"name": "Blade Runner", "year": 1982}
      #                                           ^^^^ WARNING Expected type 'bool', got 'int' instead
      """)

    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items type matching in call`() = test("""
      from typing_extensions import TypedDict

      class ExtraMovie(TypedDict, extra_items=int):
          name: str

      ExtraMovie(name="No Country for Old Men")

      ExtraMovie(name="No Country for Old Men", year=2007)

      ExtraMovie(name="No Country for Old Men", language="English")
      #                                         ^^^^^^^^^^^^^^^^^^ WARNING Expected type 'int', got 'Literal["English"]' instead

      ExtraMovie(name="Inception", year=2010, rating=8, budget="160M")
      #                                                 ^^^^^^^^^^^^^ WARNING Expected type 'int', got 'Literal["160M"]' instead

      ExtraMovie(name="Dune", year=None)
      #                       ^^^^^^^^^ WARNING Expected type 'int', got 'None' instead
      """)

    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items inherited through subclassing`() = test("""
      from typing_extensions import TypedDict

      class MovieBase(TypedDict, extra_items=int | None):
          name: str

      class InheritedMovie(MovieBase):
          year: int
      #   ^^^^ WARNING Required key 'year' is not known to 'MovieBase'

      InheritedMovie(name="Blade Runner", year=1982, budget="100M")
      #                                              ^^^^^^^^^^^^^ WARNING Expected type 'int | None', got 'Literal["100M"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items TypedDict assignable to Mapping when types match`() = test("""
      from typing_extensions import TypedDict
      from typing import Mapping

      # > A TypedDict type is :term:`assignable` to a type of the form ``Mapping[str, VT]``
      # > when all value types of the items in the TypedDict
      # > are assignable to ``VT``.
      
      class MovieExtraInt(TypedDict, extra_items=int):
          name: str
      
      class MovieExtraStr(TypedDict, extra_items=str):
          name: str

      extra_str3: MovieExtraStr = {"name": "Blade Runner", "summary": ""}
      str_mapping: Mapping[str, str] = extra_str3  # OK

      extra_int3: MovieExtraInt = {"name": "Blade Runner", "year": 1982}
      int_mapping: Mapping[str, int] = extra_int3
      #                                ^^^^^^^^^^ WARNING Expected type 'Mapping[str, int]', got 'MovieExtraInt' instead
      int_str_mapping: Mapping[str, int | str] = extra_int3  # OK
      """)

    @Test
    @TestFor(issues = ["PY-85421"])
    fun `extra_items TypedDict assignable to dict when all keys not required`() = test("""
      from typing import NotRequired, ReadOnly
      from typing_extensions import TypedDict
      
      class IntDict(TypedDict, extra_items=int):
          pass
      
      class IntDictWithNum(IntDict):
          num: NotRequired[int]
      
      def clear_intdict(x: IntDict) -> None:
          v: dict[str, int] = x  # OK
          v.clear()  # OK
      #   ^^^^^^^^^ WARNING 'clear' is not callable
      
      not_required_num_dict: IntDictWithNum = {"num": 1, "bar": 2}
      regular_dict: dict[str, int] = not_required_num_dict  # OK
      clear_intdict(not_required_num_dict)  # OK
      
      # Cases when it is NOT assignable to dict[str, VT]:
      
      # 1. Value type is not consistent with VT
      class IntDictWithStr(IntDict):
          description: NotRequired[str]
      #   ^^^^^^^^^^^ WARNING Expected type 'int', got 'str' instead
      
      not_consistent_dict: IntDictWithStr = {"description": "test"}
      inconsistent: dict[str, int] = not_consistent_dict  # Error: 'str' is not consistent with 'int'
      #                              ^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'dict[str, int]', got 'IntDictWithStr' instead
      
      # 2. Item is read-only
      class IntDictReadOnly(IntDict):
          readonly_num: NotRequired[ReadOnly[int]]
      
      readonly_dict: IntDictReadOnly = {"readonly_num": 42}
      readonly_error: dict[str, int] = readonly_dict  # Error: 'readonly_num' is read-only
      #                                ^^^^^^^^^^^^^ WARNING Expected type 'dict[str, int]', got 'IntDictReadOnly' instead
      
      # 3. Item is required
      class IntDictRequired(IntDict):
          required_num: int
      #   ^^^^^^^^^^^^ WARNING Required key 'required_num' is not known to 'IntDict'
      
      required_dict: IntDictRequired = {"required_num": 10}
      required_error: dict[str, int] = required_dict  # Error: 'required_num' is required
      #                                ^^^^^^^^^^^^^ WARNING Expected type 'dict[str, int]', got 'IntDictRequired' instead
      
      # 4. Combination: required and read-only
      class IntDictRequiredReadOnly(IntDict):
          id: ReadOnly[int]
      #   ^^ WARNING Required key 'id' is not known to 'IntDict'
      
      required_readonly_dict: IntDictRequiredReadOnly = {"id": 1}
      combined_error: dict[str, int] = required_readonly_dict  # Error: 'id' is both required and read-only
      #                                ^^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'dict[str, int]', got 'IntDictRequiredReadOnly' instead
      """)
  }

  @Nested
  inner class InspectionsUnpackKwargs {
    @Test
    @TestFor(issues = ["PY-55044"])
    fun `Unpack kwargs argument type mismatch`() = test("""
      from typing import TypedDict, Unpack

      class Movie(TypedDict):
          name: str

      def foo(**x: Unpack[Movie]):
          pass

      foo(name=1)
      #   ^^^^^^ WARNING Expected type 'str', got 'Literal[1]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76847"])
    fun `dict unpack vs Unpack TypedDict parameter`() = test(TestOptions(enablePyAnyType = false), """
      from typing import TypedDict, NotRequired, Required, Unpack
      
      class TD1(TypedDict):
          v1: Required[int]
          v2: NotRequired[str]
      
      class TD2(TD1):
          v3: Required[str]
      
      def func1(**kwargs: Unpack[TD2]) -> None: ...
      
      my_dict: dict[str, str] = {}
      my_typed_dict: TD2
      func1(**my_dict)
      #       ^^^^^^^ WARNING Expected type 'TD2', got 'dict[str, str]' instead
      func1(**my_typed_dict) # OK
      """)

    @Test
    @TestFor(issues = ["PY-76847"])
    fun `dict unpack vs unpacked dict literal`() = test(TestOptions(enablePyAnyType = false), """
      from typing import TypedDict, NotRequired, Required, Unpack
      
      class TD1(TypedDict):
          v1: Required[int]
          v2: NotRequired[str]
      
      class TD2(TD1):
          v3: Required[str]
      
      def func1(**kwargs: Unpack[TD2]) -> None: ...
      
      func1(**{'v1': 1, 'v2': 'test', 'v3': 'test'}) # OK
      func1(**{'v1': 1, 'v2': 'test', 'v3': 1})
      #                                     └ WARNING Expected type 'str', got 'int' instead
      """)

    @Test
    @TestFor(issues = ["PY-76847"])
    fun `unpacked TypedDict vs signature without TypedDict`() = test(TestOptions(enablePyAnyType = false), """
      from typing import Protocol, TypedDict, NotRequired, Required, Unpack
      
      class TD1(TypedDict):
          v1: Required[int]
          v2: NotRequired[str]
      
      class TD2(TD1):
          v3: Required[str]
      
      class TDProtocol(Protocol):
          def __call__(self, **kwargs: Unpack[TD2]) -> None:
              ...
      def foo(*, v1: int, v3: str, v2: str = "") -> None:
          ...
      def bar(*, v1: int, v3: str, v2: str = "", **kwargs) -> None:
          ...
      _: TDProtocol = foo
      #               ^^^ WARNING Expected type 'TDProtocol', got '(*, v1: int, v3: str, v2: str) -> None' instead
      _: TDProtocol = bar # OK, has **kwargs
      """)

    @Test
    @TestFor(issues = ["PY-76847"])
    fun `kwargs with not-unpacked TypedDict accepts TypedDict`() = test("""
      from typing import TypedDict, NotRequired, Required

      class TD1(TypedDict):
          v1: Required[int]
          v2: NotRequired[str]

      def func1(**kwargs: TD1) -> None: ...
      td1 = TD1(v1=1, v2="abc")
      td2 = TD1(v1=2, v2="def")
      func1(a=td1, b=td2, c="wrong")
      #                   ^^^^^^^^^ WARNING Expected type 'TD1', got 'Literal["wrong"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76847"])
    fun `ParamSpec substituted with Unpack TypedDict kwargs`() = test("""
      from typing import Callable, TypedDict, Unpack

      def g[**P](fn: Callable[P, None]) -> Callable[P, None]:
          return fn

      class Person(TypedDict):
          name: str
          age: int

      def create_person(**kwargs: Unpack[Person]):
          pass

      g(create_person)(**{"name": ""})
      #                  ^^^^^^^^^^^^ WARNING TypedDict 'Person' has missing key: 'age'
      g(create_person)(name="John", age="30")
      #                             ^^^^^^^^ WARNING Expected type 'int', got 'Literal["30"]' instead
      """)

    @Test
    @TestFor(issues = ["PY-76847"])
    fun `ParamSpec substituted with Unpack TypedDict kwargs in class`() = test("""
      from typing import Callable, TypedDict, Unpack


      class Person(TypedDict):
          name: str
          age: int

      class Factory[**P]:
          fn: Callable[P, None]

          def __init__(self, fn: Callable[P, None]):
              self.fn = fn


      def create_person(**kwargs: Unpack[Person]):
          pass


      Factory(create_person).fn(**{"name": ""})
      #                           ^^^^^^^^^^^^ WARNING TypedDict 'Person' has missing key: 'age'
      Factory(create_person).fn(name="")
      #                                └ WARNING Parameter 'age' unfilled
      """)

    @Test
    @TestFor(issues = ["PY-76847"])
    fun `ParamSpec substituted with Unpack TypedDict kwargs in same call`() = test("""
      from typing import Callable, TypedDict, Unpack
      
      
      def g[**P](fn: Callable[P, None], *args: P.args, **kwargs: P.kwargs) -> Callable[P, None]:
          return fn
      
      
      class Person(TypedDict):
          name: str
          age: int
      
      
      def create_person(**kwargs: Unpack[Person]):
          pass
      
      
      g(create_person, **{"name": ""})
      #                  ^^^^^^^^^^^^ WARNING TypedDict 'Person' has missing key: 'age'
      g(create_person, name="")
      #                       └ WARNING Parameter 'age' unfilled (from ParamSpec 'P')
      """)

    @Test
    @TestFor(issues = ["PY-88727", "PY-76847"])
    fun `fixed tuple args combined with Unpack TypedDict kwargs`() = test("""
      from typing import TypedDict, Unpack

      class Movie(TypedDict):
          name: str

      def foo(*args: *tuple[int, str], **kwargs: Unpack[Movie]) -> None: ...

      foo(1, "hello", name="test")
      foo("wrong", "hello", name="test")
      #   ^^^^^^^ WARNING Expected type 'int', got 'Literal["wrong"]' instead
      foo(1, "hello", name=42)
      #               ^^^^^^^ WARNING Expected type 'str', got 'Literal[42]' instead
      """)
  }

  @Test
  @TestFor(issues = ["PY-90291"])
  fun `TypedDict as Mapping or dict`() = test("""
    from typing import Mapping, TypedDict, NotRequired
    
    
    # A closed TypedDict has no extra items, so it is assignable to Mapping[str, VT]
    # when the value types of all its items are subtypes of VT, but it is never
    # assignable to a mutable dict[str, VT].
    class Closed1(TypedDict, closed=True, total=False):
        a: int
    
    
    class Closed2(TypedDict, closed=True):
        a: int
    
    
    c1: Closed1 = {"a": 1}
    c2: Closed2 = {"a": 1}
    
    m_int_1: Mapping[str, int] = c1
    m_int_2: Mapping[str, int] = c2
    m_obj_1: Mapping[str, object] = c1
    m_str_1: Mapping[str, str] = c1
    #                            ^^ WARNING Expected type 'Mapping[str, str]', got 'Closed1' instead
    
    d_int_1: dict[str, int] = c1
    #                         ^^ WARNING Expected type 'dict[str, int]', got 'Closed1' instead
    d_int_2: dict[str, int] = c2
    #                         ^^ WARNING Expected type 'dict[str, int]', got 'Closed2' instead
    
    
    # A TypedDict with mutable extra items is assignable to dict[str, VT] when every
    # declared item is mutable, non-required and equivalent to VT.
    class ExtraInt(TypedDict, extra_items=int):
        pass
    
    
    class ExtraIntRequired(ExtraInt):
        name: NotRequired[int]
    
    
    class ExtraIntNotRequired(TypedDict, extra_items=int, total=False):
        name: int
    
    
    class ExtraStrName(TypedDict, extra_items=int):
        name: str
    
    
    ei: ExtraInt = {}
    eir: ExtraIntRequired = {"name": 1}
    einr: ExtraIntNotRequired = {"name": 1}
    esn: ExtraStrName = {"name": "s"}
    
    m_ei: Mapping[str, int] = ei
    m_eir: Mapping[str, int] = eir
    m_esn_int: Mapping[str, int] = esn
    #                              ^^^ WARNING Expected type 'Mapping[str, int]', got 'ExtraStrName' instead
    m_esn_union: Mapping[str, int | str] = esn
    
    d_ei: dict[str, int] = ei
    d_eir: dict[str, int] = eir
    #                       ^^^ WARNING Expected type 'dict[str, int]', got 'ExtraIntRequired' instead
    d_einr: dict[str, int] = einr
    
    
    # A plain (open) TypedDict implicitly allows read-only extra items of type object,
    # so it is only assignable to Mapping[str, object] and never to dict[str, VT].
    class Open(TypedDict):
        a: int
    
    
    op: Open = {"a": 1}
    
    m_open_obj: Mapping[str, object] = op
    m_open_int: Mapping[str, int] = op
    #                               ^^ WARNING Expected type 'Mapping[str, int]', got 'Open' instead
    d_open: dict[str, int] = op
    #                        ^^ WARNING Expected type 'dict[str, int]', got 'Open' instead
    """)
}
