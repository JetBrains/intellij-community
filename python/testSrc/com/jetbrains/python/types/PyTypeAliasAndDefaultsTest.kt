// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type tests for type aliases (generic, recursive, conditional, multi-file, PEP 695 `type X = ...`),
 * `TypeVar` defaults, and classes parameterized with default generics.
 */
class PyTypeAliasAndDefaultsTest : PyCodeInsightTestCase() {

  override val defaultTestOptions = TestOptions(enablePyAnyType = false)

  @Nested
  inner class ConditionalAliases {
    @Test
    @TestFor(issues = ["PY-18427", "PY-76243"])
    fun `conditional type alias`() = test("""
      if something:
      #  ^^^^^^^^^ ERROR Unresolved reference 'something'
          Type = int
      else:
          Type = str

      expr: Type
      # └ TYPE int
      """)

    @Test
    fun `conditional generic type alias without explicit parameter`() = test("""
      if bool():
          Type = list
      else:
          Type = set

      expr: Type[str]
      # │       └ WEAK-WARNING Member 'type[set]' of 'type[list | set]' does not have attribute '__getitem__' FIXME
      # └ TYPE list[str]
      """)

    @Test
    fun `conditional generic type alias with explicit parameter`() = test("""
      from typing import TypeVar

      T = TypeVar("T")

      if bool():
          Type = list[T]
      else:
          Type = set[T]

      expr: Type[str]
      #│        └ WEAK-WARNING Member 'type[set[T]]' of 'type[list[T] | set[T]]' does not have attribute '__getitem__' FIXME
      #└ TYPE list[str]
      """)

    @Test
    fun `type alias of union of generic types`() = test("""
      from typing import TypeVar

      T = TypeVar("T")

      Type = list[T] | set[T]

      expr: Type[str]
      # │       └ WEAK-WARNING Member 'type[set[T]]' of 'UnionType | type[list[T]] | type[set[T]]' does not have attribute '__getitem__' FIXME
      # └ TYPE list[str] | set[str]
      """)

    @Test
    fun `type alias of union of generic types with different arity`() = test("""
      from typing import TypeVar

      T1 = TypeVar("T1")
      T2 = TypeVar("T2")

      Type = dict[T1, T2] | set[T2]

      expr: Type[str, int]
      #│        └ WEAK-WARNING Member 'type[set[T2]]' of 'UnionType | type[dict[T1, T2]] | type[set[T2]]' does not have attribute '__getitem__' FIXME
      #└ TYPE dict[str, int] | set[int]
      """)
  }

  @Nested
  inner class RecursiveAliasesSameFile {
    @Test
    @TestFor(issues = ["PY-18386"])
    fun `recursive type`() = test(TestOptions(assertRecursionPrevention = false), """
      from typing import Union
      
      Type = Union[int, 'Type']
      expr = 42 # type: Type
      # └ TYPE int | Unknown
      """)

    @Test
    @TestFor(issues = ["PY-18386"])
    fun `recursive type in dict value`() = test(TestOptions(assertRecursionPrevention = false), """
      from typing import Dict, Union
      
      JsonDict = Dict[str, Union[str, int, float, 'JsonDict']]
      
      def f(x: JsonDict):
          expr = x
      #   └ TYPE dict[str, str | int | float | Unknown]
      """)

    @Test
    @TestFor(issues = ["PY-18386"])
    fun `recursive type via two aliases`() = test(TestOptions(assertRecursionPrevention = false), """
      from typing import Union
      
      Type1 = Union[str, 'Type2']
      Type2 = Union[int, Type1]
      
      expr = None # type: Type1
      #└ TYPE str | int | Unknown
      """)
  }

  @Nested
  inner class RecursiveOrTrivialAliasesInAnotherFile {
    @Test
    @TestFor(issues = ["PY-31004"])
    fun `recursive type alias in another file`() = test(
      """
      from other import MyType

      expr: MyType = ...
      #│             ^^^ WARNING Expected type 'list[Any] | int', got 'EllipsisType' instead
      #└ TYPE list[Any] | int
      """,
      "other.py" to """
        from typing import List, Union
        
        MyType = Union[List['MyType'], int]
        """,
    )

    @Test
    @TestFor(issues = ["PY-34478"])
    fun `trivial type alias in another file`() = test(
      """
      from other import alias
      
      expr: alias
      #└ TYPE str
      """,
      "other.py" to "alias = str",
    )

    @Test
    @TestFor(issues = ["PY-34478"])
    fun `trivial unresolved type alias in another file`() = test(
      """
      from other import alias
      
      expr: alias
      #└ TYPE Any
      """,
      "other.py" to "alias = unresolved",
    )

    @Test
    @TestFor(issues = ["PY-34478"])
    fun `trivial recursive type alias in another file`() = test(
      """
      from other import alias

      expr: alias
      #│    ^^^^^ WARNING Invalid type annotation
      #└ TYPE Any
      """,
      "other.py" to """
        alias2 = 'alias'
        alias = alias2
        """,
    )

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `recursive type alias in another file PEP695 syntax`() = test(
      """
      from a import MyType

      expr: MyType = ...
      #│             ^^^ WARNING Expected type 'list[Any] | int', got 'EllipsisType' instead
      #└ TYPE list[Any] | int
      """,
      "a.py" to """
        from typing import List, Union
        
        type MyType = Union[List['MyType'], int]
        """,
    )

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `trivial recursive type alias in another file PEP695 syntax`() = test(
      """
      from a import alias
      
      expr: alias
      #└ TYPE Any
      """,
      "a.py" to """
        type alias2 = 'alias'
        type alias = alias2
        """,
    )

    @Test
    @TestFor(issues = ["PY-61883"])
    fun `generic type alias in another file PEP695 syntax`() = test(
      """
      from a import alias
      
      expr: alias[str, int]
      #└ TYPE dict[str, int]
      """,
      "a.py" to "type alias[T, U] = dict[T, U]",
    )
  }

  @Nested
  inner class ParameterizedGenericAliases {
    @Test
    @TestFor(issues = ["PY-29257"])
    fun `parameterized type alias for partially generic type, trailing param`() = test("""
      from typing import TypeVar
      T = TypeVar('T')
      dict_t1 = dict[str, T]
      expr: dict_t1[int]
      #└ TYPE dict[str, int]
      """)

    @Test
    @TestFor(issues = ["PY-29257"])
    fun `parameterized type alias for partially generic type, leading param`() = test("""
      from typing import TypeVar
      T = TypeVar('T')
      dict_t1 = dict[T, int]
      expr: dict_t1[str]
      #└ TYPE dict[str, int]
      """)

    @Test
    @TestFor(issues = ["PY-49582"])
    fun `parameterized type alias for generic union`() = test("""
      from typing import Awaitable, Optional, TypeVar, Union
      T = TypeVar('T')
      Input = Union[T, Awaitable[T]]
      
      def f(expr: Optional[Input[str]]):
      #     └ TYPE str | Awaitable[str] | None
          pass
      """)

    @Test
    @TestFor(issues = ["PY-29257"])
    fun `parameterized type alias preserves order of type parameters, T1 T2`() = test("""
      from typing import TypeVar
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      Alias = dict[T1, T2]
      expr: Alias[str]
      #│          ^^^ WARNING Passed type arguments do not match type parameters of type alias 'Alias'
      #└ TYPE dict[str, Any]
      """)

    @Test
    @TestFor(issues = ["PY-29257"])
    fun `parameterized type alias preserves order of type parameters, T2 T1`() = test("""
      from typing import TypeVar
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      Alias = dict[T2, T1]
      expr: Alias[str]
      #│          ^^^ WARNING Passed type arguments do not match type parameters of type alias 'Alias'
      #└ TYPE dict[str, Any]
      """)

    @Test
    @TestFor(issues = ["PY-29257"])
    fun `generic type alias parameterized with explicit Any`() = test("""
      from typing import TypeVar
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      Alias = dict[T1, T2]
      expr: Alias[Any, str]
      #│          ^^^ ERROR Unresolved reference 'Any'
      #└ TYPE dict[Any, str]
      """)

    @Test
    @TestFor(issues = ["PY-29257"])
    fun `generic type alias parameterized in two steps`() = test("""
      from typing import TypeVar
      T1 = TypeVar('T1')
      T2 = TypeVar('T2')
      Alias1 = dict[T1, T2]
      Alias2 = Alias1[int, T2]
      expr: Alias2[str]
      #└ TYPE dict[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-44905"])
    fun `generic type alias to Annotated`() = test("""
      from typing import Annotated, TypeVar
      marker = object()
      T = TypeVar("T")
      Inject = Annotated[T, marker]
      expr: Inject[int]
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-29257"])
    fun `generic type alias for tuple`() = test("""
      from typing import TypeVar
      T = TypeVar('T')
      Pair = tuple[T, T]
      expr: Pair[int]
      #└ TYPE tuple[int, int]
      """)

    @Test
    fun `chain of generic aliases with TypeVar replaced by generic type`() = test("""
      from typing import Tuple, TypeVarTuple, TypeVar
      
      T = TypeVar('T')
      T2 = TypeVar('T2')
      
      MyType = Tuple[int, T]
      MyType1 = MyType[list[T2]]
      
      t: MyType1[str]
      expr = t
      #└ TYPE tuple[int, list[str]]
      """)
  }

  @Nested
  inner class TypeVarDefaultsClassCallInstancesNewSyntax {
    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class call parameterized with one type, new syntax`() = test("""
      class slice[StartT = int, StopT = StartT, StepT = int | None]: ...
      expr = slice[str]()
      #└ TYPE slice[str, str, int | None]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class call fully parameterized, new syntax`() = test("""
      class slice[StartT = int, StopT = StartT, StepT = int | None]: ...
      expr = slice[str, bool, complex]()
      #└ TYPE slice[str, bool, complex | float | int]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class with init method reference`() = test("""
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar
      #└ TYPE type[Bar[Any, list[Any]]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class with init method reference parameterized with one type`() = test("""
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar[int]
      #└ TYPE type[Bar[int, list[int]]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class with init method call parameterized with one type and constructor arguments`() = test("""
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar[int](0, [])
      #└ TYPE Bar[int, list[int]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults class with init method call parameterized with two types and constructor arguments changing default type`() =
      test("""
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar[int, str](0, "")
      #└ TYPE Bar[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults subclassed class instance`() = test("""
      from typing import TypeVar, Generic, TypeAlias
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SubclassMe(Generic[T1, DefaultStrT]):
          x: DefaultStrT
      class Bar(SubclassMe[int, DefaultStrT]): ...
      expr = Bar()
      #└ TYPE Bar[str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults default overriden by explicit constructor argument`() = test("""
      from typing import TypeVar, Generic
      T = TypeVar("T", default=int)
      T1 = TypeVar("T1", default=str)
      T2 = TypeVar("T2", default=bool)
      class Box(Generic[T, T1, T2]):
          def __init__(self, a: T = None, b: T1 = None, c: T2 = None):
              self.value = a
              self.value1 = b
              self.value2 = c
      expr = Box("str")
      #└ TYPE Box[str, str, bool]
      """)
  }

  @Nested
  inner class TypeVarDefaultsClassVariables {
    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar with defaults class variable`() = test("""
      from typing import TypeVar, Generic
      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Test(Generic[DefaultIntT]):
          x: DefaultIntT
      expr = Test().x
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar with defaults class variable overriden`() = test("""
      from typing import TypeVar, Generic
      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Test(Generic[DefaultIntT]):
          x: DefaultIntT
      expr = Test[str]().x
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar with defaults class variable new syntax`() = test("""
      class Test[DefaultIntT = int]:
          x: DefaultIntT
      expr = Test().x
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar with default class variable type defined via another TypeVar with new syntax`() = test("""
      class Test[T = int, U = T]:
          x: U
      
      expr = Test().x
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar with defaults class variable overriden new syntax`() = test("""
      class Test[DefaultIntT = int]:
          x: DefaultIntT
      expr = Test[str]().x
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar with defaults class variable access via reference`() = test("""
      from typing import TypeVar, Generic
      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Test(Generic[DefaultIntT]):
          x: DefaultIntT
      expr = Test.x
      #└ TYPE int
      """)
  }

  @Nested
  inner class MixedDefaultsNonDefaults {
    @Test
    @TestFor(issues = ["PY-71002"])
    fun `mixed TypeVars with defaults and non-defaults`() = test("""
      from typing import TypeVar, Generic
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]): ...
      expr = AllTheDefaults[int, complex]()
      #└ TYPE AllTheDefaults[int, complex | float | int, str, int, bool]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `mixed TypeVars with defaults and non-defaults reference type`() = test("""
      from typing import TypeVar, Generic
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]): ...
      expr = AllTheDefaults[int, complex]
      #└ TYPE type[AllTheDefaults[int, complex | float | int, str, int, bool]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `mixed TypeVars with defaults and non-defaults one type param missing`() = test("""
      from typing import TypeVar, Generic
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]): ...
      expr = AllTheDefaults[int]()
      #│                    ^^^ WARNING Passed type arguments do not match type parameters [T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT] of class 'AllTheDefaults'
      #└ TYPE AllTheDefaults[int, Any, str, int, bool]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `mixed TypeVars with defaults and non-defaults two type params missing`() = test("""
      from typing import TypeVar, Generic
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]): ...
      expr = AllTheDefaults()
      #└ TYPE AllTheDefaults[Any, Any, str, int, bool]
      """)
  }

  @Nested
  inner class LongTypeVarChains {
    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults long TypeVar to TypeVar chain`() = test("""
      from typing import TypeVar, Generic
      
      T = TypeVar("T", default=str)
      T1 = TypeVar("T1", default=T)
      T2 = TypeVar("T2", default=T1)
      T3 = TypeVar("T3", default=T2)
      T4 = TypeVar("T4", default=T3)
      T5 = TypeVar("T5", default=T4)
      T6 = TypeVar("T6", default=T5)
      T7 = TypeVar("T7", default=T6)
      T8 = TypeVar("T8", default=T7)
      
      class Box(Generic[T, T1, T2, T3, T4, T5, T6, T7, T8]):
          value: T8
      expr = Box().value
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults long TypeVar to TypeVar chain with first overriden`() = test("""
      from typing import TypeVar, Generic
      
      T = TypeVar("T", default=str)
      T1 = TypeVar("T1", default=T)
      T2 = TypeVar("T2", default=T1)
      T3 = TypeVar("T3", default=T2)
      T4 = TypeVar("T4", default=T3)
      T5 = TypeVar("T5", default=T4)
      T6 = TypeVar("T6", default=T5)
      T7 = TypeVar("T7", default=T6)
      T8 = TypeVar("T8", default=T7)
      
      class Box(Generic[T, T1, T2, T3, T4, T5, T6, T7, T8]):
          value: T8
      expr = Box[list]().value
      #└ TYPE list[Any]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults long TypeVar to TypeVar chain with first and second overriden`() = test("""
      from typing import TypeVar, Generic
      
      T = TypeVar("T", default=str)
      T1 = TypeVar("T1", default=T)
      T2 = TypeVar("T2", default=T1)
      T3 = TypeVar("T3", default=T2)
      T4 = TypeVar("T4", default=T3)
      T5 = TypeVar("T5", default=T4)
      T6 = TypeVar("T6", default=T5)
      T7 = TypeVar("T7", default=T6)
      T8 = TypeVar("T8", default=T7)
      
      class Box(Generic[T, T1, T2, T3, T4, T5, T6, T7, T8]):
          value: T8
      expr = Box[list, int]().value
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar defaults long TypeVar to TypeVar chain with multiple defaults`() = test("""
      from typing import TypeVar, Generic
      
      T = TypeVar("T", default=str)
      T1 = TypeVar("T1", default=T)
      T2 = TypeVar("T2", default=float)
      T3 = TypeVar("T3", default=T2)
      T4 = TypeVar("T4", default=bool)
      T5 = TypeVar("T5", default=T4)
      T6 = TypeVar("T6", default=T5)
      T7 = TypeVar("T7", default=T6)
      T8 = TypeVar("T8", default=T7)
      
      class Box(Generic[T, T1, T2, T3, T4, T5, T6, T7, T8]):
          value: T | T8 | T3 = None
      
      expr = Box().value
      #└ TYPE str | bool | float | int
      """)
  }

  @Nested
  inner class NewStylePep695TypeAliasesWithDefaults {
    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults`() = test("""
      type Alias[T = int, U = str] = dict[T, U]
      expr: Alias
      #└ TYPE dict[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults one overriden`() = test("""
      type Alias[T = int, U = str] = dict[T, U]
      expr: Alias[bool]
      #└ TYPE dict[bool, str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults two overriden`() = test("""
      type Alias[T = int, U = str] = dict[T, list[U]]
      x: Alias[bool, int]
      expr = x
      #└ TYPE dict[bool, list[int]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults nested`() = test("""
      type Alias[T = int, U = str] = dict[T, list[U]]
      expr: Alias
      #└ TYPE dict[int, list[str]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults nested one overriden`() = test("""
      type Alias[T = int, U = str] = dict[T, list[U]]
      expr: Alias[bool]
      #└ TYPE dict[bool, list[str]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults nested two overriden`() = test("""
      type Alias[T = int, U = str] = dict[T, list[U]]
      expr: Alias[bool, bool]
      #└ TYPE dict[bool, list[bool]]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with TypeVar only`() = test("""
      type Alias[T = int] = T
      expr: Alias
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with TypeVar only overriden`() = test("""
      type Alias[T = int] = T
      expr: Alias[bool]
      #└ TYPE bool
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults union`() = test("""
      type Alias[T = int, U = str] = T | U
      expr: Alias
      #└ TYPE int | str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults one type of union overriden`() = test("""
      type Alias[T = int, U = str] = T | U
      expr: Alias[bool]
      #└ TYPE bool | str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults union overriden`() = test("""
      type Alias[T = int, U = str] = T | U
      expr: Alias[bool, float]
      #└ TYPE bool | float | int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias union changed order`() = test("""
      type Alias[T = int, U = str] = U | list[T]
      expr: Alias
      #└ TYPE str | list[int]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias union changed order defaults overriden`() = test("""
      type Alias[T = int, U = str] = U | list[T]
      expr: Alias[float, bool]
      #└ TYPE bool | list[float | int]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias one without default`() = test("""
      from typing import TypeVar, TypeAlias
      T = TypeVar("T")
      U = TypeVar("U", default=str)
      B = TypeVar("B", default=float)
      Alias : TypeAlias = dict[T, U] | list[B]
      expr: Alias
      #└ TYPE dict[Any, str] | list[float | int]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias one without default`() = test("""
      type Alias[T, U = str, B = float] = dict[T, U] | list[B]
      expr: Alias
      #└ TYPE dict[Any, str] | list[float | int]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias one without default parameterized`() = test("""
      type Alias[T, U = str, B = float] = dict[T, U] | list[B] | T
      expr: Alias[int]
      #└ TYPE dict[int, str] | list[float | int] | int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias one without default all overriden`() = test("""
      type Alias[T, U = str, B = float] = dict[T, U] | list[B] | T
      expr: Alias[int, int, int]
      #└ TYPE dict[int, int] | list[int] | int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults prev TypeVar as default`() = test("""
      type Alias[T, U = T] = T | list[U]
      expr: Alias[str]
      #└ TYPE str | list[str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with defaults TypeVar chain`() = test("""
      type Alias[T = str, T1 = T, T2 = T1, T3 = T2, T4 = T3] = T4
      expr: Alias
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with all default types`() = test("""
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate
      type ReturnTupleAlias[T = float, **P = [str, str], *Ts = Unpack[tuple[str, int]]] = Callable[Concatenate[*Ts, P], T]
      def f() -> ReturnTupleAlias: ...
      expr = f()
      #└ TYPE (str, int, str, str) -> float | int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with all default types return type overriden`() = test("""
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate
      type ReturnTupleAlias[T = float, **P = [str, str], *Ts = Unpack[tuple[str, int]]] = Callable[Concatenate[*Ts, P], T]
      def f() -> ReturnTupleAlias[list[str]]: ...
      expr = f()
      #└ TYPE (str, int, str, str) -> list[str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with all default types return and ParamSpec type overriden`() = test("""
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate
      type ReturnTupleAlias[T = float, **P = [str, str], *Ts = Unpack[tuple[str, int]]] = Callable[Concatenate[*Ts, P], T]
      def f() -> ReturnTupleAlias[list[str], [float, float]]: ...
      expr = f()
      #└ TYPE (str, int, float | int, float | int) -> list[str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with all default types all overridden`() = test("""
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate
      type ReturnTupleAlias[T = float, **P = [str, str], *Ts = Unpack[tuple[str, int]]] = Callable[Concatenate[*Ts, P], T]
      def f() -> ReturnTupleAlias[list[str], [float, float], str, float, bool, list[bool]]: ...
      expr = f()
      #└ TYPE (str, float | int, bool, list[bool], float | int, float | int) -> list[str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `TypeVar with default class method type defined via another TypeVar with new syntax`() = test("""
      class Test[T = int, U = T]:
          def foo(self) -> U: ...
      expr = Test().foo()
      #└ TYPE int
      """)
  }

  @Nested
  inner class OldStyleTypeAliasAliasesWithDefaults {
    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with defaults`() = test("""
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T')
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SomethingWithNoDefaults(Generic[T, T2]): ...
      MyAlias: TypeAlias = SomethingWithNoDefaults[int, DefaultStrT]
      expr: MyAlias
      #└ TYPE SomethingWithNoDefaults[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with parameterized instance`() = test("""
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T')
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SomethingWithNoDefaults(Generic[T, T2]): ...
      MyAlias: TypeAlias = SomethingWithNoDefaults[int, DefaultStrT]
      expr: MyAlias[bool]
      #└ TYPE SomethingWithNoDefaults[int, bool]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with defaults tuple`() = test("""
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T', default=int)
      U = TypeVar('U', default=str)
      MyAlias: TypeAlias = T | U
      expr: MyAlias
      #└ TYPE int | str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with defaults dict`() = test("""
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T', default=int)
      U = TypeVar('U', default=str)
      MyAlias: TypeAlias = dict[T, U]
      expr: MyAlias
      #└ TYPE dict[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with defaults dict one default overriden`() = test("""
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T', default=int)
      U = TypeVar('U', default=str)
      MyAlias: TypeAlias = dict[T, U]
      expr: MyAlias[str]
      #└ TYPE dict[str, str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with defaults dict two defaults overriden`() = test("""
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T', default=int)
      U = TypeVar('U', default=str)
      MyAlias: TypeAlias = dict[T, U]
      expr: MyAlias[str, float]
      #└ TYPE dict[str, float | int]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with all default types`() = test("""
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate, TypeAlias, TypeVar
      T = TypeVar('T', default = float)
      P = ParamSpec('P', default=[str ,str])
      Ts = TypeVarTuple('Ts', default=Unpack[tuple[str, int]])
      ReturnTupleAlias: TypeAlias = Callable[Concatenate[*Ts, P], T]
      def g() -> ReturnTupleAlias: ...
      expr = g()
      #└ TYPE (str, int, str, str) -> float | int
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with parameterized non-default in declaration`() = test("""
      from typing import TypeVar, Generic, TypeAlias
      T = TypeVar('T')
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class Triple(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]):
          val: dict[T1, T2] | DefaultStrT
      Alias: TypeAlias = Triple[str, int]
      e: Alias = None
      #          ^^^^ WARNING Expected type 'Triple[str, int, str, int, bool]', got 'None' instead
      expr = e.val
      #└ TYPE dict[str, int] | str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `explicit Any not substituted by defaults`() = test("""
      class Test[T = str, T1 = int, T2 = bool]: ...
      expr = Test[Any, Any]()
      #│          │    ^^^ ERROR Unresolved reference 'Any'
      #│          ^^^ ERROR Unresolved reference 'Any'
      #└ TYPE Test[Any, Any, bool]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias parameterized in multiple steps`() = test("""
      from typing import Union
      type A[T1, T2, T3 = str] = Union[T1, T2, T3]
      type B[T1, T2 = int] = A[T1, T2]
      type C[T1] = B[T1]
      type D = C[float]
      expr: D
      #└ TYPE float | int | str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `mixed old and new style type aliases parameterized in multiple steps`() = test("""
      from typing import Union, TypeVar, TypeAlias
      T = TypeVar("T")
      T1 = TypeVar("T1", default=int)
      type A[T1, T2, T3 = str] = Union[T1, T2, T3]
      B: TypeAlias = A[T, T1]
      C: TypeAlias = B[T]
      type D = C[float]
      expr: D
      #└ TYPE float | int | str
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with assigned subscription expression`() = test("""
      type my_dict[K = float, V = bool] = dict[K, V]
      type myIntStrDict = my_dict[int, str]
      expr: myIntStrDict
      #└ TYPE dict[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `new style type alias with assigned subscription expression aliasing union`() = test("""
      type Alias[T = int, U = str] = U | list[T]
      type Alias2 = Alias[float, bool]
      expr: Alias2
      #└ TYPE bool | list[float | int]
      """)

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `old style type alias with assigned subscription expression aliasing union`() = test("""
      from typing import TypeVar, Generic, TypeAlias
      T = TypeVar("T", default=int)
      U = TypeVar("U", default=str)
      Alias: TypeAlias = U | list[T]
      Alias2: TypeAlias = Alias[float, bool]
      expr: Alias2
      #└ TYPE float | int | list[bool]
      """)
  }

  @Nested
  inner class DefaultsDefinedInAnotherFile {
    @Test
    @TestFor(issues = ["PY-71002"])
    fun `class with default generics defined in another file`() = test(
      """
      from mod import StackOfIntsByDefault
      stack = StackOfIntsByDefault()
      expr = stack.pop()
      #└ TYPE int
      """,
      "mod.py" to """
        from typing import Generic, TypeVar
        
        T = TypeVar('T', default=int)
        
        class StackOfIntsByDefault(Generic[T]):
            def pop(self) -> T: ...
        """,
    )

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `class with default generics defined in another file default overriden`() = test(
      """
      from mod import StackOfIntsByDefault
      stack = StackOfIntsByDefault[str]()
      expr = stack.pop()
      #└ TYPE str
      """,
      "mod.py" to """
        from typing import Generic, TypeVar
        
        T = TypeVar('T', default=int)
        
        class StackOfIntsByDefault(Generic[T]):
            def pop(self) -> T: ...
        """,
    )

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `class with default generics defined in another file attribute access`() = test(
      """
      from mod import Box
      expr = Box.val
      #└ TYPE int | str
      """,
      "mod.py" to """
        from typing import Generic, TypeVar
        
        T = TypeVar('T', default=int)
        U = TypeVar('U', default=str)
        
        class Box(Generic[T, U]):
            val: T | U
        """,
    )

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `class with new style default generics defined in another file`() = test(
      """
      from mod import StackOfIntsByDefault
      stack = StackOfIntsByDefault()
      expr = stack.pop()
      #└ TYPE int
      """,
      "mod.py" to """
        class StackOfIntsByDefault[T = int]:
            def pop(self) -> T: ...
        """,
    )

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with defaults defined in another file`() = test(
      """
      from mod import StrIntDict
      expr: StrIntDict
      #└ TYPE dict[int, str]
      """,
      "mod.py" to """
        from typing import TypeVar, TypeAlias
        T = TypeVar('T', default = int)
        U = TypeVar('U', default = str)
        StrIntDict: TypeAlias = dict[T, U]
        """,
    )

    @Test
    @TestFor(issues = ["PY-71002"])
    fun `type alias with defaults defined in another file aliasing generic class`() = test(
      """
      from mod import MyAlias
      expr = MyAlias[bool]()
      #└ TYPE SomethingWithNoDefaults[int, bool]
      """,
      "mod.py" to """
        from typing import TypeVar, TypeAlias, Generic
        T = TypeVar('T')
        T2 = TypeVar('T2')
        U = TypeVar('U')
        DefaultStrT = TypeVar('DefaultStrT', default = str)
        class SomethingWithNoDefaults(Generic[T, T2]): ...
        MyAlias: TypeAlias = SomethingWithNoDefaults[int, DefaultStrT]
        """,
    )
  }

  @Nested
  inner class NonParameterizedGenericWithDefaultUsedElsewhere {
    @Test
    @TestFor(issues = ["PY-82454"])
    fun `method returning type parameter called on non-parameterized generic with default`() = test("""
      class Box[T=str]:
          def m(self) -> T:
              ...
      
      def f() -> Box:
          ...
      
      expr = f().m()
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-82454"])
    fun `attribute of type parameter type accessed on non-parameterized generic with default`() = test("""
      class Box[T=str]:
          attr: T
      
      def f() -> Box:
          ...
      
      expr = f().attr
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-82454"])
    fun `non-parameterized generic with default used in other type`() = test("""
      class Box[T=str]:
          def m(self) -> T:
              ...
      
      def f() -> list[Box]:
          ...
      
      expr = f()
      #└ TYPE list[Box[str]]
      """)

    @Test
    @TestFor(issues = ["PY-82454"])
    fun `method returning Self called on non-parameterized generic with default`() = test("""
      from typing import Self
      
      class Box[T=str]:
          def m(self) -> Self:
              ...
      
      def f() -> Box:  # not parameterized, simulating open() -> TextIOWrapper
          ...
      
      expr = f().m()
      #└ TYPE Box[str]
      """)

    @Test
    @TestFor(issues = ["PY-82454"])
    fun `method returning type parameterized with Self called of non-parameterized generic with default`() = test("""
      from typing import Self
      
      class Box[T=str]:
          def m(self) -> list[Self]:
              ...
      
      def f() -> Box:  # not parameterized, simulating open() -> TextIOWrapper
          ...
      
      expr = f().m()
      #└ TYPE list[Box[str]]
      """)

    @Test
    @TestFor(issues = ["PY-82454"])
    fun `method returning Self called on non-parameterized generic with default and bound`() = test("""
      from typing import Self
      
      class Box[T : str = str]:
          def m(self) -> Self:
              ...
      
      def f() -> Box:  # not parameterized, simulating open() -> TextIOWrapper
          ...
      
      expr = f().m()
      #└ TYPE Box[str]
      """)
  }
}
