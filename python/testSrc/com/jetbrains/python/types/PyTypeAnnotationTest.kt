// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyTypeHintsInspection
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Subsystems.Inspections
@Layers.Functional
@TestFor(classes = [PyTypeHintsInspection::class])
class PyTypeAnnotationTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-28243"])
  fun `TypeVar and target name`() = test("""
    from typing import TypeVar

    T0 = TypeVar('T0')
    T1 = TypeVar('T2')
    #            ^^^^ WARNING The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned
    """)

  @Test
  @TestFor(issues = ["PY-28243"])
  fun `TypeVar placement`() = test("""
    from typing import List, TypeVar

    T0 = TypeVar('T0')
    a: List[T0]
    #       ^^ WARNING Unbound type variable
    b: List[TypeVar('T1')]
    #       ^^^^^^^^^^^^^ WARNING A 'TypeVar()' expression must always directly be assigned to a variable
    """)

  @Test
  @TestFor(issues = ["PY-28243"])
  fun `TypeVar redefinition`() = test("""
    from typing import TypeVar

    T0 = TypeVar('T0')
    print(T0)
    T0 = TypeVar('T0')
    #\ WARNING Type variables must not be redefined
    """)

  @Test
  @TestFor(issues = ["PY-28124"])
  fun `TypeVar bivariant`() = test("""
    from typing import TypeVar

    T1 = TypeVar('T1', contravariant=True, covariant=True)
    #    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Bivariant type variables are not supported
    true = True
    T2 = TypeVar('T2', contravariant=true, covariant=true)
    #    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Bivariant type variables are not supported
    """)

  @Test
  @TestFor(issues = ["PY-28124"])
  fun `TypeVar constraints and bound`() = test("""
    from typing import TypeVar

    T2 = TypeVar('T2', int, str, bound=str)
    #    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR Constraints cannot be combined with bound=…
    """)

  @Test
  @TestFor(issues = ["PY-28124"])
  fun `TypeVar - number of constraints`() = test("""
    from typing import TypeVar

    T1 = TypeVar('T1', int)
    #    ^^^^^^^^^^^^^^^^^^ ERROR A single constraint is not allowed
    T2 = TypeVar('T2', int, str)
    """)

  @Test
  @TestFor(issues = ["PY-28124"])
  fun `TypeVar name as Literal`() = test("""
    from typing import TypeVar

    name = 'T0'
    T0 = TypeVar(name)
    #            ^^^^ WARNING 'TypeVar()' expects a string literal as first argument
    T1 = TypeVar('T1')
    """)

  @Test
  @TestFor(issues = ["PY-28243"])
  fun `TypeVar parameterized constraints`() = test("""
    from typing import TypeVar, List

    T1 = TypeVar('T1', int, str)

    T2 = TypeVar('T2', int, List[T1])
    #                       │    ^^ WARNING Unbound type variable
    #                       ^^^^^^^^ WARNING Constraints cannot be parametrized by type variables
    T3 = TypeVar('T3', bound=List[T1])
    #                        │    ^^ WARNING Unbound type variable
    #                        ^^^^^^^^ WARNING Constraints cannot be parametrized by type variables

    T4 = TypeVar('T4', int, List[int])
    T5 = TypeVar('T5', bound=List[int])

    my_int = int
    my_str = str
    T11 = TypeVar('T11', my_int, my_str)

    my_list_t1 = List[T1]
    T22 = TypeVar('T22', int, my_list_t1)
    T33 = TypeVar('T33', bound=my_list_t1)

    my_list_int = List[int]
    T44 = TypeVar('T44', int, my_list_int)
    T55 = TypeVar('T55', bound=my_list_int)
    """)

  @Test
  @TestFor(issues = ["PY-28227"])
  fun `plain Generic inheritance`() = test("""
    from typing import Generic

    class A(Generic):
    #       ^^^^^^^ ERROR Cannot inherit from plain 'Generic'
        pass

    B = Generic
    class C(B):
    #       └ ERROR Cannot inherit from plain 'Generic'
        pass
    """)

  @Test
  @TestFor(issues = ["PY-28227"])
  fun `Generic parameters types`() = test("""
    from typing import Generic, Protocol, TypeVar

    class A1(Generic[0]):
    #                └ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    class B1(Generic[int]):
    #                ^^^ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    class B11(Protocol[int]):
    #                  ^^^ ERROR Parameters to 'Protocol[...]' must all be type variables
        pass

    class A2(Generic[0, 0]):
    #                │  └ ERROR Parameters to 'Generic[...]' must all be type variables
    #                └ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    class B2(Generic[int, int]):
    #                │    ^^^ ERROR Parameters to 'Generic[...]' must all be type variables
    #                ^^^ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    null = 0
    class A3(Generic[null]):
    #                ^^^^ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    my_int = int
    class B3(Generic[my_int]):
    #                ^^^^^^ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    T = TypeVar('T')
    S = TypeVar('S')

    class C1(Generic[T]):
        pass

    class C2(Generic[T, S]):
        pass

    my_t = T
    class C3(Generic[my_t]):
    #                ^^^^ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    class D1:
        pass

    class D2:
        pass

    class E1(Generic[D1]):
    #                ^^ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    class F1(Generic[D1, D2]):
    #                │   ^^ ERROR Parameters to 'Generic[...]' must all be type variables
    #                ^^ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    my_d = D1
    class E2(Generic[my_d]):
    #                ^^^^ ERROR Parameters to 'Generic[...]' must all be type variables
        pass
    """)

  @Test
  @TestFor(issues = ["PY-28227"])
  fun `Generic parameters duplication`() = test("""
    from typing import Generic, TypeVar

    T = TypeVar('T')

    class C(Generic[T, T]):
    #                  └ ERROR Parameters to 'Generic[...]' must all be unique
        pass

    B = Generic
    class A(B[T, T]):
    #            └ ERROR Parameters to 'Generic[...]' must all be unique
        pass

    T1 = T
    class D(Generic[T1, T]):
    #               ^^ ERROR Parameters to 'Generic[...]' must all be type variables
        pass
    """)

  @Test
  @TestFor(issues = ["PY-28227"])
  fun `Generic duplication`() = test("""
    from typing import Generic, TypeVar

    T = TypeVar('T')
    S = TypeVar('S')

    class C(Generic[T], Generic[S]):
    #                   ^^^^^^^^^^ ERROR Cannot inherit from 'Generic[...]' multiple times
        pass

    B = Generic
    class D(B[T], Generic[S]):
    #             ^^^^^^^^^^ ERROR Cannot inherit from 'Generic[...]' multiple times
        pass

    E = Generic[T]
    class A(E, Generic[S]):
    #          ^^^^^^^^^^ ERROR Cannot inherit from 'Generic[...]' multiple times
        pass
    """)

  @Test
  @TestFor(issues = ["PY-28227"])
  fun `Generic completeness`() = test("""
    from typing import Generic, TypeVar, Iterable, Protocol

    T = TypeVar('T')
    S = TypeVar('S')

    class C(Generic[T], Iterable[S]):  # ISSUES *
    #      ^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR 'Generic[...]' or 'Protocol[...]' should list all type variables (S)
        pass

    class P(Iterable[S], Protocol[T]):
    #      ^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR 'Generic[...]' or 'Protocol[...]' should list all type variables (S)
        pass

    B = Generic
    D = T
    class A(B[D], Iterable[S]):  # ISSUES *
    #      │  └ ERROR Parameters to 'Generic[...]' must all be type variables
    #      ^^^^^^^^^^^^^^^^^^^ ERROR 'Generic[...]' or 'Protocol[...]' should list all type variables (S)
        pass

    class E(Generic[T], Iterable[T]):  # ISSUES *
        pass

    class F(B[D]):
    #         └ ERROR Parameters to 'Generic[...]' must all be type variables
        pass

    class G(Iterable[T]):  # ISSUES *
        pass
    """)

  @Test
  @TestFor(issues = ["PY-31147"])
  fun `Generic completeness partially specialized`() = test("""
    from typing import TypeVar, Generic, Dict

    T = TypeVar("T")

    class C(Generic[T], Dict[int, T]):
        pass
    """)

  @Test
  @TestFor(issues = ["PY-78767"])
  fun `Generic metaclasses are not supported`() = test(
    TestOptions(assertRecursionPrevention = false),
    """
    from typing import Any, Generic, TypeVar

    T = TypeVar("T")

    class MyMetaClass(type, Generic[T]): ...

    class MyClass1(Generic[T], metaclass=MyMetaClass[T]): ...
    #                                    ^^^^^^^^^^^^^^ WARNING Metaclass cannot be generic
    class MyClass2(metaclass=MyMetaClass[Any]): ...
    """)

  @Test
  @TestFor(issues = ["PY-76866"])
  fun `unbound type parameter`() = test("""
    from typing import Generic, TypeVar, TypeVarTuple, ParamSpec, TypeAlias, Unpack

    T = TypeVar('T')
    S = TypeVar('S')

    T1 = TypeVar('T1', default=T)
    T2 = TypeVarTuple('T2', default=Unpack[tuple[S, T]])
    T3 = ParamSpec('T3', default=[S, T])

    Alias1 = T
    Alias2 = dict[S, T]
    Alias3: TypeAlias = list[T]
    type Alias4[K, V] = dict[K, V]

    v1: T
    #   └ WARNING Unbound type variable
    v2: list[T]
    #        └ WARNING Unbound type variable

    list[T]()
    #    └ WARNING Unbound type variable

    def f1(x: T) -> None:
        a1: T
        a2: list[T] = []
        a3: S
    #       └ WARNING Unbound type variable
        a4: list[S] = []
    #            └ WARNING Unbound type variable

        list[T]()
        list[S]()
    #        └ WARNING Unbound type variable

    def f2() -> T:
        x: T
        raise Exception()

    class Bar(Generic[T]):
        attr1: T
        attr2: list[T] = []
        attr3: S
    #          └ WARNING Unbound type variable
        attr4: list[S] = []
    #               └ WARNING Unbound type variable

        def do_something(self, x: S) -> S: ...
        def do_something_else(self, other: 'Bar[T]'): ...
    """)

  @Test
  @TestFor(issues = ["PY-78878"])
  fun `Generic class can not use type variables from outer scope`() = test("""
    from typing import TypeVar, Generic

    T = TypeVar('T')
    S = TypeVar('S')

    def a_fun(x: T) -> None:
        a_list: list[T] = []

        class MyGeneric(Generic[T]): ...
    #         ^^^^^^^^^ WARNING Some type variables (T) are already in use by an outer scope

    def a_fun_new_syntax1[U]() -> None:
        class MyGeneric(Generic[U]): ...
    #         ^^^^^^^^^ WARNING Some type variables (U) are already in use by an outer scope

    def a_fun_new_syntax2[U](u: U) -> None:
        class MyGeneric(Generic[U]): ...
    #         ^^^^^^^^^ WARNING Some type variables (U) are already in use by an outer scope

    class Outer(Generic[T]):
        class Bad(list[T]): ...
    #         ^^^ WARNING Some type variables (T) are already in use by an outer scope
        class AlsoBad:
            x: list[T]
    #               └ WARNING Unbound type variable

        class Inner(list[S]): ...
        attr: Inner[T]

    class OuterNewSyntax[U]:
        class Bad(Generic[U]): ...
    #         ^^^ WARNING Some type variables (U) are already in use by an outer scope
    """)

  @Test
  @TestFor(issues = ["PY-82835"])
  fun `type parameter is already in use by outer scope`() = test("""
    from typing import TypeAlias

    T = 0

    class ClassA[T](list[T]):
        T = 1

        def method1[T](self): ...
    #               └ WARNING Type parameter 'T' is already in use by an outer scope

        def method2[T](self, x=T): ...
    #               └ WARNING Type parameter 'T' is already in use by an outer scope

        def method3[T](self, x: T): ...
    #               └ WARNING Type parameter 'T' is already in use by an outer scope

        class Inner[T]: ...
    #               └ WARNING Type parameter 'T' is already in use by an outer scope
    """)

  @Test
  fun `self annotation uses class-scoped type parameters`() = test("""
    class MyClass[T1, T2]:
        def __init__(self: MyClass[T2, T1]) -> None: ...
    #                      ^^^^^^^^^^^^^^^ WARNING Class-scoped type variables should not be used in the annotation for 'self' parameter of '__init__' method
    """)

  @Test
  fun `inconsistent TypeVar order`() = test("""
    from typing import Generic, TypeVar

    T1 = TypeVar('T1')
    T2 = TypeVar('T2')

    class Grandparent(Generic[T1, T2]): ...
    class Parent(Grandparent[T1, T2]): ...
    class BadChild(Parent[T1, T2], Grandparent[T2, T1]): ...
    #                              ^^^^^^^^^^^^^^^^^^^ WARNING Generic base class 'Grandparent' is inherited with inconsistent type arguments: 'Grandparent[T1, T2]' and 'Grandparent[T2, T1]'
    """)

  @Test
  fun `inconsistent TypeVar order diamond`() = test("""
    from typing import Generic, TypeVar

    T1 = TypeVar('T1')
    T2 = TypeVar('T2')

    class Base(Generic[T1, T2]): ...
    class Left(Base[T1, T2]): ...
    class Right(Base[T2, T1]): ...
    class BadDiamond(Left[T1, T2], Right[T1, T2]): ...
    #                              ^^^^^^^^^^^^^ WARNING Generic base class 'Base' is inherited with inconsistent type arguments: 'Base[T1, T2]' and 'Base[T2, T1]'
    """)

  @Test
  fun `consistent TypeVar order with reordered intermediate`() = test("""
    from typing import Generic, TypeVar

    T1 = TypeVar('T1')
    T2 = TypeVar('T2')

    class Base(Generic[T1, T2]): ...
    class Reordered(Generic[T1, T2], Base[T2, T1]): ...
    class GoodChild(Reordered[T1, T2], Base[T2, T1]): ...
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Any`() = test("""
    from typing import Any

    class A:
        pass

    assert isinstance(A(), Any)
    #                      ^^^ ERROR 'Any' cannot be used with instance and class checks
    B = Any
    assert issubclass(A, B)
    #                    └ ERROR 'Any' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on NoReturn`() = test("""
    from typing import NoReturn

    class A:
        pass

    assert isinstance(A(), NoReturn)
    #                      ^^^^^^^^ ERROR 'NoReturn' cannot be used with instance and class checks
    B = NoReturn
    assert issubclass(A, B)
    #                    └ ERROR 'NoReturn' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on TypeVar`() = test("""
    from typing import TypeVar

    T = TypeVar("T")

    class A:
        pass

    a = A()

    assert isinstance(a, TypeVar)
    assert issubclass(A, TypeVar)

    assert isinstance(a, T)
    #                    └ ERROR Type variables cannot be used with instance and class checks
    assert issubclass(A, T)
    #                    └ ERROR Type variables cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Union before 310`() = test(
    TestOptions(languageLevel = LanguageLevel.PYTHON39),
    """
    from typing import Union

    class A:
        pass

    a = A()

    assert isinstance(a, Union)
    #                    ^^^^^ ERROR 'Union' cannot be used with instance and class checks
    B = Union
    assert issubclass(A, B)
    #                    └ ERROR 'Union' cannot be used with instance and class checks

    assert isinstance(a, Union[int, str])
    #                    ^^^^^^^^^^^^^^^ ERROR 'Union' cannot be used with instance and class checks
    assert issubclass(A, B[int, str])
    #                    ^^^^^^^^^^^ ERROR 'Union' cannot be used with instance and class checks
    C = B[int, str]
    assert issubclass(A, C)
    #                    └ ERROR 'Union' cannot be used with instance and class checks
    assert isinstance(a, Union[str, Union[str, Union[list, dict]]])
    #                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR 'Union' cannot be used with instance and class checks
    assert isinstance(a, Union[str, Union[str, Union[list[int], dict]]])
    #                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR 'Union' cannot be used with instance and class checks
    assert isinstance(a, int | str)
    #                    ^^^^^^^^^ ERROR Python version 3.9 does not allow writing union types as X | Y
    assert isinstance(a, int | list[str])
    #                    ^^^^^^^^^^^^^^^ ERROR Python version 3.9 does not allow writing union types as X | Y
    assert issubclass(A, int | str)
    #                    ^^^^^^^^^ ERROR Python version 3.9 does not allow writing union types as X | Y
    assert issubclass(A, int | list[str])
    #                    ^^^^^^^^^^^^^^^ ERROR Python version 3.9 does not allow writing union types as X | Y
    """)

  @Test
  @TestFor(issues = ["PY-44974"])
  fun `instance and class checks on Union from future annotations`() = test(
    TestOptions(languageLevel = LanguageLevel.PYTHON39),
    """
    from typing import Union
    from __future__ import annotations

    class A:
        pass

    a = A()

    assert isinstance(a, Union)
    #                    ^^^^^ ERROR 'Union' cannot be used with instance and class checks
    B = Union
    assert issubclass(A, B)
    #                    └ ERROR 'Union' cannot be used with instance and class checks

    assert isinstance(a, Union[int, str])
    #                    ^^^^^^^^^^^^^^^ ERROR 'Union' cannot be used with instance and class checks
    assert issubclass(A, B[int, str])
    #                    ^^^^^^^^^^^ ERROR 'Union' cannot be used with instance and class checks
    C = B[int, str]
    assert issubclass(A, C)
    #                    └ ERROR 'Union' cannot be used with instance and class checks
    assert isinstance(a, Union[str, Union[str, Union[list, dict]]])
    #                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR 'Union' cannot be used with instance and class checks
    assert isinstance(a, Union[str, Union[str, Union[list[int], dict]]])
    #                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ERROR 'Union' cannot be used with instance and class checks
    assert isinstance(a, int | str)
    #                    ^^^^^^^^^ ERROR Python version 3.9 does not allow writing union types as X | Y
    assert isinstance(a, int | list[str])
    #                    ^^^^^^^^^^^^^^^ ERROR Python version 3.9 does not allow writing union types as X | Y
    assert issubclass(A, int | str)
    #                    ^^^^^^^^^ ERROR Python version 3.9 does not allow writing union types as X | Y
    assert issubclass(A, int | list[str])
    #                    ^^^^^^^^^^^^^^^ ERROR Python version 3.9 does not allow writing union types as X | Y
    """)

  @Test
  @TestFor(issues = ["PY-44974"])
  fun `instance and class checks on Union`() = test("""
    from typing import Union

    class A:
        pass

    a = A()

    assert isinstance(a, Union)
    #                    ^^^^^ ERROR 'Union' cannot be used with instance and class checks
    B = Union
    assert issubclass(A, B)
    #                    └ ERROR 'Union' cannot be used with instance and class checks

    assert isinstance(a, Union[int, str])
    assert issubclass(A, B[int, str])
    C = B[int, str]
    assert issubclass(A, C)
    assert isinstance(a, Union[str, Union[str, Union[list, dict]]])
    assert isinstance(a, Union[str, Union[str, Union[list[int], dict]]])
    #                                                ^^^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    assert isinstance(a, int | str)
    assert isinstance(a, int | list[str])
    #                          ^^^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    assert issubclass(A, int | str)
    assert issubclass(A, int | list[str])
    #                          ^^^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Optional before 310`() = test(
    TestOptions(languageLevel = LanguageLevel.PYTHON39),
    """
    from typing import Optional

    class A:
        pass

    a = A()

    assert isinstance(a, Optional)
    #                    ^^^^^^^^ ERROR 'Optional' cannot be used with instance and class checks
    B = Optional
    assert issubclass(A, B)
    #                    └ ERROR 'Optional' cannot be used with instance and class checks

    assert isinstance(a, Optional[int])
    #                    ^^^^^^^^^^^^^ ERROR 'Optional' cannot be used with instance and class checks
    assert issubclass(A, B[int])
    #                    ^^^^^^ ERROR 'Optional' cannot be used with instance and class checks
    C = B[int]
    assert issubclass(A, C)
    #                    └ ERROR 'Optional' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Optional from future annotations`() = test(
    TestOptions(languageLevel = LanguageLevel.PYTHON39),
    """
    from typing import Optional
    from __future__ import annotations

    class A:
        pass

    a = A()

    assert isinstance(a, Optional)
    #                    ^^^^^^^^ ERROR 'Optional' cannot be used with instance and class checks
    B = Optional
    assert issubclass(A, B)
    #                    └ ERROR 'Optional' cannot be used with instance and class checks

    assert isinstance(a, Optional[int])
    #                    ^^^^^^^^^^^^^ ERROR 'Optional' cannot be used with instance and class checks
    assert issubclass(A, B[int])
    #                    ^^^^^^ ERROR 'Optional' cannot be used with instance and class checks
    C = B[int]
    assert issubclass(A, C)
    #                    └ ERROR 'Optional' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Optional`() = test("""
    from typing import Optional

    class A:
        pass

    a = A()

    assert isinstance(a, Optional)
    #                    ^^^^^^^^ ERROR 'Optional' cannot be used with instance and class checks
    B = Optional
    assert issubclass(A, B)
    #                    └ ERROR 'Optional' cannot be used with instance and class checks

    assert isinstance(a, Optional[int])
    assert issubclass(A, B[int])
    C = B[int]
    assert issubclass(A, C)
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on ClassVar`() = test("""
    from typing import ClassVar

    class A:
        pass

    a = A()

    assert isinstance(a, ClassVar)
    #                    ^^^^^^^^ ERROR 'ClassVar' cannot be used with instance and class checks
    B = ClassVar
    assert issubclass(A, B)
    #                    └ ERROR 'ClassVar' cannot be used with instance and class checks

    assert isinstance(a, ClassVar[int])
    #                    ^^^^^^^^^^^^^ ERROR 'ClassVar' cannot be used with instance and class checks
    assert issubclass(A, B[int])
    #                    ^^^^^^ ERROR 'ClassVar' cannot be used with instance and class checks
    C = B[int]
    assert issubclass(A, C)
    #                    └ ERROR 'ClassVar' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Generic`() = test("""
    from typing import TypeVar, Generic

    T = TypeVar("T")

    class A:
        pass

    assert isinstance(A(), Generic)
    B = Generic
    assert issubclass(A, B)

    assert isinstance(A(), Generic[T])
    #                      ^^^^^^^^^^ ERROR 'Generic' cannot be used with instance and class checks
    assert issubclass(A, B[T])
    #                    ^^^^ ERROR 'Generic' cannot be used with instance and class checks
    C = B[T]
    assert issubclass(A, C)
    #                    └ ERROR 'Generic' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-34945"])
  fun `instance and class checks on Final`() = test("""
    from typing import TypeVar
    from typing_extensions import Final

    T = TypeVar("T")

    class A:
        pass

    a = A()

    assert isinstance(a, Final)
    #                    ^^^^^ ERROR 'Final' cannot be used with instance and class checks
    B = Final
    assert issubclass(A, B)
    #                    └ ERROR 'Final' cannot be used with instance and class checks

    assert isinstance(a, Final[T])
    #                    ^^^^^^^^ ERROR 'Final' cannot be used with instance and class checks
    assert issubclass(A, B[T])
    #                    ^^^^ ERROR 'Final' cannot be used with instance and class checks
    C = B[T]
    assert issubclass(A, C)
    #                    └ ERROR 'Final' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-35235"])
  fun `instance and class checks on Literal`() = test("""
    from typing_extensions import Literal

    class A:
        pass

    a = A()

    assert isinstance(a, Literal)
    #                    ^^^^^^^ ERROR 'Literal' cannot be used with instance and class checks
    B = Literal
    assert issubclass(A, B)
    #                    └ ERROR 'Literal' cannot be used with instance and class checks

    assert isinstance(a, Literal[1])
    #                    ^^^^^^^^^^ ERROR 'Literal' cannot be used with instance and class checks
    assert issubclass(A, B[1])
    #                    ^^^^ ERROR 'Literal' cannot be used with instance and class checks
    C = B[1]
    assert issubclass(A, C)
    #                    └ ERROR 'Literal' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-42334"])
  fun `instance and class checks on TypeAlias`() = test("""
    from typing import TypeAlias

    class A:
        pass

    a = A()

    assert isinstance(a, TypeAlias)
    #                    ^^^^^^^^^ ERROR 'TypeAlias' cannot be used with instance and class checks
    assert issubclass(A, TypeAlias)
    #                    ^^^^^^^^^ ERROR 'TypeAlias' cannot be used with instance and class checks
    B = TypeAlias
    assert isinstance(a, B)
    #                    └ ERROR 'TypeAlias' cannot be used with instance and class checks
    assert issubclass(A, B)
    #                    └ ERROR 'TypeAlias' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on generic inheritor`() = test("""
    from typing import TypeVar, List

    T = TypeVar("T")

    class A:
        pass

    assert isinstance(A(), List)
    B = List
    assert issubclass(A, B)

    assert isinstance(A(), List[T])
    #                      ^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    assert issubclass(A, B[T])
    #                    ^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    C = B[T]
    assert issubclass(A, C)
    #                    └ ERROR Parameterized generics cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Tuple`() = test("""
    from typing import Tuple

    class A:
        pass

    assert isinstance(A(), Tuple)  # ISSUES *
    B = Tuple
    assert issubclass(A, B)

    assert isinstance(A(), Tuple[int, str])
    #                      ^^^^^^^^^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    assert issubclass(A, B[int, str])
    #                    ^^^^^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    C = B[int, str]
    assert issubclass(A, C)
    #                    └ ERROR Parameterized generics cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Type`() = test("""
    from typing import Type

    class A:
        pass

    a = A()

    assert isinstance(a, Type)  # ISSUES *
    B = Type
    assert issubclass(A, B)

    assert isinstance(a, Type[int])
    #                    ^^^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    assert issubclass(A, B[int])
    #                    ^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    C = B[int]
    assert issubclass(A, C)
    #                    └ ERROR Parameterized generics cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Callable`() = test("""
    from typing import Callable

    class A:
        pass

    assert isinstance(A(), Callable)
    B = Callable
    assert issubclass(A, B)

    assert isinstance(A(), Callable[..., str])
    #                      ^^^^^^^^^^^^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    assert issubclass(A, B[..., str])
    #                    ^^^^^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    C = B[..., str]
    assert issubclass(A, C)
    #                    └ ERROR Parameterized generics cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on Protocol`() = test("""
    from typing import Protocol, TypeVar

    class A:
        pass

    a = A()

    T = TypeVar("T")

    assert isinstance(a, Protocol)
    #                    ^^^^^^^^ ERROR Only @runtime_checkable protocols can be used with instance and class checks
    B = Protocol
    assert issubclass(A, B)
    #                    └ ERROR Only @runtime_checkable protocols can be used with instance and class checks

    assert isinstance(a, Protocol[T])
    #                    ^^^^^^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    assert issubclass(A, B[T])
    #                    ^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    C = B[T]
    assert issubclass(A, C)
    #                    └ ERROR Parameterized generics cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on user class`() = test("""
    from typing import Generic, TypeVar

    class A:
        pass

    T = TypeVar("T")

    class D(Generic[T]):
        pass

    assert isinstance(A(), D)
    B = D
    assert issubclass(A, B)

    assert isinstance(A(), D[int])
    #                      ^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    assert issubclass(A, B[int])
    #                    ^^^^^^ ERROR Parameterized generics cannot be used with instance and class checks
    C = B[int]
    assert issubclass(A, C)
    #                    └ ERROR Parameterized generics cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-28249"])
  fun `instance and class checks on unknown`() = test("""
    from m1 import D  # ISSUES *

    class A:
        pass

    assert isinstance(A(), D)
    B = D
    assert issubclass(A, B)

    assert isinstance(A(), D[int])
    assert issubclass(A, B[int])
    C = B[int]
    assert issubclass(A, C)
    """)

  @Test
  @TestFor(issues = ["PY-31788"])
  fun `instance and class checks on generic parameter`() = test("""
    from typing import List, Type, TypeVar

    T = TypeVar("T")

    class A:
        pass

    def foo(p1: T, p2: Type[T], p3: List[T]):
        assert isinstance(A(), p1)
    #                          ^^ ERROR Type variables cannot be used with instance and class checks
        assert issubclass(A, p1)
    #                        ^^ ERROR Type variables cannot be used with instance and class checks

        assert isinstance(A(), p2)
        assert issubclass(A, p2)

        assert isinstance(A(), p3)
    #                          ^^ ERROR Type variables cannot be used with instance and class checks
        assert issubclass(A, p3)
    #                        ^^ ERROR Type variables cannot be used with instance and class checks
    """)

  @Test
  fun `TypedDict with instance and class checks`() = test("""
    from typing import TypedDict

    class Movie(TypedDict):
        name: str
        year: int

    Movie2 = TypedDict('Movie2', {'name': str, 'year': int})

    class A:
        pass

    def foo(d):
      if isinstance(d, Movie):
    #                  ^^^^^ ERROR TypedDict type cannot be used with instance and class checks
          pass

      if isinstance(d, Movie2):
    #                  ^^^^^^ ERROR TypedDict type cannot be used with instance and class checks
          pass

    M = Movie
    if issubclass(A, M):
    #                └ ERROR TypedDict type cannot be used with instance and class checks
        pass

    M2 = Movie2
    if issubclass(A, M2):
    #                ^^ ERROR TypedDict type cannot be used with instance and class checks
        pass
    """)

  @Test
  fun `TypedDict as TypeVar bound`() = test("""
    from typing import TypedDict, TypeVar

    class Movie(TypedDict):
        name: str
        year: int

    T = TypeVar("T", bound=TypedDict)
    #                      ^^^^^^^^^ WARNING TypedDict is not allowed as a bound for a TypeVar
    U = TypeVar("U", bound=Movie)
    """)

  @Test
  @TestFor(issues = ["PY-16853"])
  fun `parentheses and typing`() = test("""
    from typing import Union, TypeAlias

    def a(b: Union(int, str)):
    #        ^^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets
        pass

    def c(d):
        # type: (Union(int, str)) -> None
    #            ^^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets
        pass

    def e(f: Union()):
    #        ^^^^^^^ ERROR Generics should be specified through square brackets
        pass

    def g(h):
        # type: (Union()) -> None
    #            ^^^^^^^ ERROR Generics should be specified through square brackets
        pass

    v1 = Union(int, str)
    #    ^^^^^^^^^^^^^^^ WARNING '_SpecialForm' object is not callable
    v2 = None  # type: Union(int, str)
    #                  ^^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets

    U = Union
    def i(j: U(int, str)):
    #        ^^^^^^^^^^^ ERROR Generics should be specified through square brackets
        pass

    v3 = U(int, str)
    #    ^^^^^^^^^^^ WARNING '_SpecialForm' object is not callable

    with open("x") as bar:  # type: Union(int,str)
    #                               ^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets
        pass

    for x in []:  # type: Union(int,str)
    #                     ^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets
        pass

    A1: TypeAlias = Union(int, str)
    #               ^^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets
    A2: TypeAlias = 'Union(int, str)'
    #               │^^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets
    #               ^^^^^^^^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    A3 = Union(int, str)  # type: TypeAlias
    #    ^^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets
    A4 = 'Union(int, str)'  # type: TypeAlias
    #    │^^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets
    #    ^^^^^^^^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    """)

  @Test
  @TestFor(issues = ["PY-57155"])
  fun `parentheses in Annotated`() = test("""
    from typing import Annotated
    from typing_extensions import Annotated as AnnotatedExt

    def a(x: Annotated[str, dict(key="value")]):
        pass

    def b(x: Annotated[Annotated[str, dict(key="value")], ""]):
        pass

    def c(x: AnnotatedExt[str, dict(key="value")]):
        pass

    def d(x: AnnotatedExt[AnnotatedExt[str, dict(key="value")], ""]):
        pass

    def e(x: Annotated[str, list[dict(key="value")]]):
    #                            ^^^^^^^^^^^^^^^^^ ERROR Invalid type argument
       pass

    def f(x: Annotated[dict(key=1), ""]):
    #                  ^^^^^^^^^^^ WARNING Generics should be specified through square brackets
       pass
    """)

  @Test
  @TestFor(issues = ["PY-32634"])
  fun `parentheses in assignment`() = test("""
    from typing import DefaultDict, TypeAlias

    example = DefaultDict(int)

    ExampleAlias: TypeAlias = DefaultDict(int)
    #                         ^^^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets

    type ExampleType = DefaultDict(int)
    #                  ^^^^^^^^^^^^^^^^ ERROR Generics should be specified through square brackets
    """)

  @Test
  @TestFor(issues = ["PY-16853"])
  fun `parentheses and custom`() = test("""
    from typing import Generic, TypeVar, TypeAlias

    T = TypeVar("T")

    class A(Generic[T]):
        def __init__(self, v=None):
            pass

    def a(b: A(int)):
    #        ^^^^^^ WARNING Generics should be specified through square brackets
    #        ^^^^^^ WARNING Invalid type annotation
        pass

    def c(d):
        # type: (A(int)) -> None
    #            ^^^^^^ WARNING Generics should be specified through square brackets
        pass

    def e(f: A()):
    #        ^^^ WARNING Generics should be specified through square brackets
    #        ^^^ WARNING Invalid type annotation
        pass

    def g(h):
        # type: (A()) -> None
    #            ^^^ WARNING Generics should be specified through square brackets
        pass

    v1 = A(int)
    v2 = None  # type: A(int)
    #                  ^^^^^^ WARNING Generics should be specified through square brackets

    U = A
    def i(j: U(int)):
    #        ^^^^^^ WARNING Generics should be specified through square brackets
    #        ^^^^^^ WARNING Invalid type annotation
        pass

    v3 = None  # type: U(int)
    #                  ^^^^^^ WARNING Generics should be specified through square brackets

    A1: TypeAlias = A(int)
    #               ^^^^^^ WARNING Assigned value of type alias must be a correct type
    #               ^^^^^^ WARNING Generics should be specified through square brackets
    A2: TypeAlias = 'A(int)'
    #               │^^^^^^ WARNING Generics should be specified through square brackets
    #               ^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    A3 = A(int)  # type: TypeAlias
    #    ^^^^^^ WARNING Assigned value of type alias must be a correct type
    #    ^^^^^^ WARNING Generics should be specified through square brackets
    A4 = 'A(int)'  # type: TypeAlias
    #    │^^^^^^ WARNING Generics should be specified through square brackets
    #    ^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    """)

  @Test
  @TestFor(issues = ["PY-20530"])
  fun `Callable parameters`() = test("""
    from typing import Callable, TypeAlias

    a: Callable[..., str]
    b: Callable[[int], str]
    c: Callable[[int, str], str]

    d: Callable[...]
    #           ^^^ ERROR 'Callable' must be used as 'Callable[[arg, ...], result]'
    e: Callable[int, str]
    #           ^^^ ERROR 'Callable' first parameter must be a parameter expression
    f: Callable[int, str, str]
    #           ^^^^^^^^ ERROR 'Callable' must be used as 'Callable[[arg, ...], result]'
    g: Callable[(int, str), str]
    #           ^^^^^^^^^^ ERROR 'Callable' first parameter must be a parameter expression
    h: Callable[int]
    #           ^^^ ERROR 'Callable' must be used as 'Callable[[arg, ...], result]'
    h: Callable[(int), str]
    #           ^^^^^ ERROR 'Callable' first parameter must be a parameter expression

    A1: TypeAlias = Callable[int]
    #                        ^^^ ERROR 'Callable' must be used as 'Callable[[arg, ...], result]'
    A2: TypeAlias = 'Callable[int]'
    #                         ^^^ ERROR 'Callable' must be used as 'Callable[[arg, ...], result]'
    A3 = Callable[int]  # type: TypeAlias
    #             ^^^ ERROR 'Callable' must be used as 'Callable[[arg, ...], result]'
    A4 = 'Callable[int]'  # type: TypeAlias
    #              ^^^ ERROR 'Callable' must be used as 'Callable[[arg, ...], result]'
    """)

  @Test
  @TestFor(issues = ["PY-20530"])
  fun self() = test("""
    class A:
        def method(self, i: int):
            v1: self.B
    #           ^^^^ ERROR Invalid type 'self'
            v2 = self.B()  # type: self.B
    #                              ^^^^ ERROR Invalid type 'self'
            print(self.B)

        class B:
            pass

    class self:
        pass

    v: self
    """)

  @Test
  @TestFor(issues = ["PY-20530"])
  fun `tuple unpacking`() = test("""
    from typing import Any

    def undefined() -> Any: ...

    a1 = undefined()  # type: int

    b1, (c1, d1) = undefined()  # type: int, (int, str)
    e1, (f1, g1), h1 = undefined()  # type: int, (str, int), str

    b2, (c2, d2) = undefined()  # type: int, (int)
    #                                   ^^^^^^^^^^ WARNING Type comment cannot be matched with unpacked variables
    e2, (f2, g2), h2 = undefined()  # type: int, (str), str
    #                                       ^^^^^^^^^^^^^^^ WARNING Type comment cannot be matched with unpacked variables
    """)

  @Test
  @TestFor(issues = ["PY-20530"])
  fun `annotation and type comment`() = test("""
    a: int = 1  # type: int
    #│          ^^^^^^^^^^^ WARNING Types specified both in a type comment and annotation
    #^^^^^ WARNING Types specified both in a type comment and annotation

    def foo(a: int  # type: int
    #        │      ^^^^^^^^^^^ WARNING Types specified both in a type comment and annotation
    #        ^^^^^ WARNING Types specified both in a type comment and annotation
            ,):
        pass

    def bar(a: int) -> int:
    #   ^^^ WARNING Types specified both in a type comment and annotation
        # type: (int) -> int
    #   ^^^^^^^^^^^^^^^^^^^^ WARNING Types specified both in a type comment and annotation
        pass

    def baz1(a: int):
    #   ^^^^ WARNING Types specified both in a type comment and annotation
        # type: (int) -> int
    #   ^^^^^^^^^^^^^^^^^^^^ WARNING Types specified both in a type comment and annotation
        pass

    def baz2(a) -> int:
    #   ^^^^ WARNING Types specified both in a type comment and annotation
        # type: (int) -> int
    #   ^^^^^^^^^^^^^^^^^^^^ WARNING Types specified both in a type comment and annotation
        pass
    """)

  @Test
  @TestFor(issues = ["PY-20530"])
  fun `valid type comment and parameters`() = test("""
    from typing import Type

    class A:
        pass

    class Bar(A):
        # self is specified
        def spam11(self):
            # type: (Bar) -> None
            pass

        def egg11(self, a, b):
            # type: (Bar, str, bool) -> None
            pass

        # self is specified
        def spam12(self):
            # type: (A) -> None
            pass

        def egg12(self, a, b):
            # type: (A, str, bool) -> None
            pass

        # self is not specified
        def spam2(self):
            # type: () -> None
            pass

        def egg2(self, a, b):
            # type: (str, bool) -> None
            pass

        # cls is not specified
        @classmethod
        def spam3(cls):
            # type: () -> None
            pass

        @classmethod
        def egg3(cls, a, b):
            # type: (str, bool) -> None
            pass

        # cls is specified
        @classmethod
        def spam41(cls):
            # type: (Type[Bar]) -> None
            pass

        @classmethod
        def egg41(cls, a, b):
            # type: (Type[Bar], str, bool) -> None
            pass

        # cls is specified
        @classmethod
        def spam42(cls):
            # type: (Type[A]) -> None
            pass

        @classmethod
        def egg42(cls, a, b):
            # type: (Type[A], str, bool) -> None
            pass

        @staticmethod
        def spam5():
            # type: () -> None
            pass

        @staticmethod
        def egg5(a, b):
            # type: (str, bool) -> None
            pass

        def baz(self, a, b, c, d):
            # type: (...) -> None
            pass
    """)

  @Test
  @TestFor(issues = ["PY-20530"])
  fun `invalid type comment and parameters`() = test("""
    from typing import Type

    class Bar:
        # self is specified
        def spam1(self):
            # type: (Bar, int) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type signature has too many arguments
            pass

        def egg11(self, a, b):
            # type: (Bar, int, str, bool) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type signature has too many arguments
            pass

        def egg12(self, a, b):
            # type: (Bar) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^ WARNING Type signature has too few arguments
            pass

        # self is not specified
        def spam2(self):
            # type: (int) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^ WARNING The type of self 'int' is not a supertype of its class 'Bar'
            pass

        def egg2(self, a, b):
            # type: (int, str, bool) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING The type of self 'int' is not a supertype of its class 'Bar'
            pass

        # cls is not specified
        @classmethod
        def spam3(cls):
            # type: (int) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^ WARNING The type of self 'int' is not a supertype of its class 'type[Bar]'
            pass

        @classmethod
        def egg3(cls, a, b):
            # type: (int, str, bool) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING The type of self 'int' is not a supertype of its class 'type[Bar]'
            pass

        # cls is specified
        @classmethod
        def spam4(cls):
            # type: (Type[Bar], int) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type signature has too many arguments
            pass

        @classmethod
        def egg41(cls, a, b):
            # type: (Type[Bar], int, str, bool) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type signature has too many arguments
            pass

        @classmethod
        def egg42(cls, a, b):
            # type: (Type[Bar]) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type signature has too few arguments
            pass

        @staticmethod
        def spam5():
            # type: (int) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^ WARNING Type signature has too many arguments
            pass

        @staticmethod
        def egg51(a, b):
            # type: (int, str, bool) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type signature has too many arguments
            pass

        @staticmethod
        def egg52(a, b):
            # type: (int) -> None
    #       ^^^^^^^^^^^^^^^^^^^^^ WARNING Type signature has too few arguments
            pass
    """)

  @Test
  @TestFor(issues = ["PY-20530"])
  fun `typing member parameters`() = test("""
    from typing import Callable, List

    foo1: Callable[[int], [int]]
    #                     ^^^^^ ERROR Parameters to generic types must be types
    foo2: Callable[[int], [int, str]]
    #                     ^^^^^^^^^^ ERROR Parameters to generic types must be types
    foo3: List[[int]]
    #          ^^^^^ ERROR Parameters to generic types must be types
    foo4: List[[int, str]]
    #          ^^^^^^^^^^ ERROR Parameters to generic types must be types

    l1 = [int]
    l2 = [int, str]

    foo5: Callable[[int], l1]
    #                     ^^ ERROR Parameters to generic types must be types
    foo6: Callable[[int], l2]
    #                     ^^ ERROR Parameters to generic types must be types
    foo7: List[l1]
    #          ^^ ERROR Parameters to generic types must be types
    foo8: List[l2]
    #          ^^ ERROR Parameters to generic types must be types
    """)

  @Test
  @TestFor(issues = ["PY-32530"])
  fun `annotation and ignore comment`() = test("""
    def foo(a: str):  # type: ignore
        pass
    def bar(a: Unknown):  # type: ignore[no-untyped-def, name-defined]
        pass
    """)

  @Test
  fun `annotating non-self attribute`() = test("""
    class A:
        def method(self, b):
            b.a: int = 1
    #       ^^^ WARNING Non-self attribute could not be type hinted

    class B:
        a = ""

    B.a: str = "2"
    #^^ WARNING Non-self attribute could not be type hinted

    def func(a):
        a.xxx: str = "2"
    #   ^^^^^ WARNING Non-self attribute could not be type hinted
    """)

  @Test
  @TestFor(issues = ["PY-35235"])
  fun `typing extensions Literal`() = test("""
    from typing_extensions import Literal, LiteralString

    a: Literal[1 + 2]
    #          ^^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    b: Literal[4j]
    #          ^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    c: Literal[3.14]
    #          ^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    d: Literal[...]
    #          ^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types

    class A:
        pass

    e: Literal[Literal[A]]
    #                  └ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    f = Literal[A]
    #           └ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    g: Literal[f]
    #          └ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types

    h: Literal[-1]
    i: Literal['abb']
    j: Literal[False]
    k: Literal[None]
    l: Literal[Literal[-3]]

    ONE = Literal[1]

    m = Literal[ONE]

    def f(c: bool):
        v: Literal[1 if c else 2]
    #              ^^^^^^^^^^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types

    expr: LiteralString = "aba"
    n: Literal[f"hello {expr}"]
    #          ^^^^^^^^^^^^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    """)

  @Test
  @TestFor(issues = ["PY-79227"])
  fun `Enum Literal`() = test("""
    from enum import Enum, member, nonmember
    from typing import Literal, Any

    class Color(Enum):
        R = 1
        G = 2
        RED = R

        foo = nonmember(3)

        @member
        def bar(self): ...

    class A:
        X = Color.R

    class SuperEnum(Enum):
        PINK = "PINK", "hot"
        FLOSS = "FLOSS", "sweet"

    tuple = 1, "ab"
    o = object()
    def get_object() -> object: ...
    def get_any() -> Any: ...

    class E(Enum):
        FOO = tuple
        BAR = o
        BUZ = get_object()
        QUX = get_any()

        def meth(self): ...

        meth2 = meth

    v1: Literal[A.X]
    #           ^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types

    X = Color.R
    v2: Literal[X]
    #           └ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types

    v3: Literal[Color.G]
    v4: Literal[Color.RED]
    v5: Literal[Color.foo]
    #           ^^^^^^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    v6: Literal[Color.bar]

    v7: Literal[SuperEnum.PINK]

    v8: Literal[E.FOO]
    v9: Literal[E.BAR]
    v10: Literal[E.BUZ]
    v11: Literal[E.QUX]
    v12: Literal[E.meth2]
    #            ^^^^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    """)

  @Test
  @TestFor(issues = ["PY-79227"])
  fun `Enum Literal multi-file`() = test(
    """
    from typing import Literal
    from m import *

    v1: Literal[A.X]
    #           ^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types

    X = Color.R
    v2: Literal[X]
    #           └ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types

    v3: Literal[Color.G]
    v4: Literal[Color.RED]
    v5: Literal[Color.foo]
    #           ^^^^^^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    v6: Literal[Color.bar]

    v7: Literal[SuperEnum.PINK]

    v8: Literal[E.FOO]
    v9: Literal[E.BAR]
    v10: Literal[E.BUZ]
    v11: Literal[E.QUX]
    v12: Literal[E.meth2]
    #            ^^^^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    """,
    "m.py" to """
      from enum import Enum, member, nonmember
      from typing import Any

      class Color(Enum):
          R = 1
          G = 2
          RED = R
          foo = nonmember(3)
          @member
          def bar(self): ...

      class A:
          X = Color.R

      class SuperEnum(Enum):
          PINK = "PINK", "hot"
          FLOSS = "FLOSS", "sweet"

      tuple = 1, "ab"
      o = object()
      def get_object() -> object: ...
      def get_any() -> Any: ...

      class E(Enum):
          FOO = tuple
          BAR = o
          BUZ = get_object()
          QUX = get_any()

          def meth(self): ...

          meth2 = meth
      """,
  )

  @Test
  @TestFor(issues = ["PY-35235"])
  fun `Literal without arguments`() = test("""
    from typing import Literal

    a: Literal = 1
    #  ^^^^^^^ WARNING 'Literal' must have at least one parameter
    b = 2  # type: Literal
    #              ^^^^^^^ WARNING 'Literal' must have at least one parameter
    """)

  @Test
  @TestFor(issues = ["PY-35235"])
  fun `non-plain string as typing Literal index`() = test("""
    from typing import Literal

    a: Literal[f"1"] = "1"
    #          ^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    """)

  @Test
  fun `parameterized builtin collections before 39`() = test(
    TestOptions(languageLevel = LanguageLevel.PYTHON38),
    """
    xs: type[str]
    #   ^^^^^^^^^ WARNING Builtin 'type' cannot be parameterized directly
    ys: tuple[int, str]
    #   ^^^^^^^^^^^^^^^ WARNING Builtin 'tuple' cannot be parameterized directly
    zs: dict[int, str]
    #   ^^^^^^^^^^^^^^ WARNING Builtin 'dict' cannot be parameterized directly
    """)

  @Test
  @TestFor(issues = ["PY-42418"])
  fun `parameterized builtin collections`() = test("""
    xs: type[str]
    ys: tuple[int, str]
    zs: dict[int, str]
    """)

  @Test
  @TestFor(issues = ["PY-41847"])
  fun `typing Annotated`() = test("""
    from typing import Annotated

    a: Annotated[1]
    #            └ WARNING 'Annotated' must be called with at least two arguments
    b: Annotated[int, 1]
    c: Annotated[...]
    #            ^^^ WARNING 'Annotated' must be called with at least two arguments

    class A:
        pass

    d: Annotated[A, '']
    e: Annotated[Annotated[A, True]]
    #            ^^^^^^^^^^^^^^^^^^ WARNING 'Annotated' must be called with at least two arguments
    f: Annotated[Annotated[A], '']
    #                      └ WARNING 'Annotated' must be called with at least two arguments
    g: Annotated[[], 1]
    #            ^^ ERROR *
    """)

  @Test
  @TestFor(issues = ["PY-89188"])
  fun `Annotated metadata`() = test("""
    from typing import Annotated

    a: Annotated[int, [], print("asdf")]
    """)

  @Test
  @TestFor(issues = ["PY-41847"])
  fun `instance and class checks on Annotated`() = test("""
    from typing import Annotated

    class A:
        pass

    a = A()

    assert isinstance(a, Annotated)
    #                    ^^^^^^^^^ ERROR 'Annotated' cannot be used with instance and class checks
    B = Annotated
    assert issubclass(A, B)
    #                    └ ERROR 'Annotated' cannot be used with instance and class checks

    assert isinstance(a, Annotated[1])
    #                    ^^^^^^^^^^^^ ERROR 'Annotated' cannot be used with instance and class checks
    assert issubclass(A, B[1])
    #                    ^^^^ ERROR 'Annotated' cannot be used with instance and class checks
    C = B[int, 2]
    assert issubclass(A, C)
    #                    └ ERROR 'Annotated' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-41847"])
  fun `Annotated without arguments`() = test("""
    from typing import Annotated

    a: Annotated = 1
    #  ^^^^^^^^^ WARNING 'Annotated' must be called with at least two arguments
    b = 2  # type: Annotated[int]
    #                        ^^^ WARNING 'Annotated' must be called with at least two arguments
    """)

  @Test
  @TestFor(issues = ["PY-42334"])
  fun `parametrized TypeAlias in expression`() = test("""
    from typing import TypeAlias

    Alias = TypeAlias[int]
    #                 ^^^ ERROR 'TypeAlias' cannot be parameterized
    """)

  @Test
  @TestFor(issues = ["PY-42334"])
  fun `parametrized TypeAlias in annotation`() = test("""
    from typing import TypeAlias

    Alias: TypeAlias[int]
    #      ^^^^^^^^^ WARNING 'TypeAlias' must be used as standalone type hint
    """)

  @Test
  @TestFor(issues = ["PY-42334"])
  fun `non-top-level TypeAlias`() = test("""
    from typing import Final, TypeAlias

    Alias: Final[TypeAlias] = str
    #            ^^^^^^^^^ WARNING 'TypeAlias' must be used as standalone type hint
    """)

  @Test
  @TestFor(issues = ["PY-42334"])
  fun `not initialized TypeAlias`() = test("""
    from typing import TypeAlias

    Alias: TypeAlias
    #^^^^ WARNING Type alias must be immediately initialized
    """)

  @Test
  @TestFor(issues = ["PY-42334"])
  fun `not top-level TypeAlias`() = test("""
    from typing import TypeAlias

    def func():
        Alias: TypeAlias = str
    #   ^^^^^ WARNING Type alias must be top-level declaration
    """)

  @Test
  @TestFor(issues = ["PY-46602"])
  fun `no inspection TypedDict in Python 38`() = test(
    TestOptions(languageLevel = LanguageLevel.PYTHON38),
    """
    from __future__ import annotations

    def hello(i: dict[str, str]):
        return i
    """)

  @Test
  @TestFor(issues = ["PY-50401"])
  fun `ParamSpec name as Literal`() = test("""
    from typing import ParamSpec

    name = 'T0'
    T0 = ParamSpec(name)
    #              ^^^^ WARNING 'ParamSpec()' expects a string literal as first argument
    T1 = ParamSpec('T1')
    """)

  @Test
  @TestFor(issues = ["PY-50401"])
  fun `ParamSpec name and target name equality`() = test("""
    from typing import ParamSpec

    T0 = ParamSpec('T1')
    #              ^^^^ WARNING The argument to 'ParamSpec()' must be a string equal to the variable name to which it is assigned
    T1 = ParamSpec('T1')
    """)

  @Test
  @TestFor(issues = ["PY-50930"])
  fun `no inspection in Callable parameter ParamSpec from typing extensions`() = test("""
    from typing import Callable, TypeVar
    from typing_extensions import ParamSpec

    P = ParamSpec("P")
    R = TypeVar("R")
    def foo(it: Callable[P, R]) -> Callable[P, R]: ...
    """)

  @Test
  @TestFor(issues = ["PY-53104"])
  fun `instance and class checks on typing Self`() = test("""
    from typing import Self

    class A:
        pass

    class B:
        def foo(self: Self):
            assert isinstance(A(), Self)
    #                              ^^^^ ERROR 'Self' cannot be used with instance and class checks
            assert issubclass(A, Self)
    #                            ^^^^ ERROR 'Self' cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-53104"])
  fun `typing Self subscription`() = test("""
    from typing import Self, Generic, TypeVar

    T = TypeVar("T")

    class A(Generic[T]):
        def foo(self):
            x: Self[int]
    #               ^^^ ERROR 'Self' cannot be parameterized
    """)

  @Test
  @TestFor(issues = ["PY-53104"])
  fun `typing Self annotation outside class`() = test("""
    from typing import Self

    def foo() -> Self:
    #            ^^^^ WARNING Cannot use 'Self' outside class
        pass
    """)

  @Test
  @TestFor(issues = ["PY-53104"])
  fun `typing Self annotation for variable outside class`() = test("""
    from typing import Self

    something: Self | None = None
    #          ^^^^ WARNING Cannot use 'Self' outside class
    """)

  @Test
  @TestFor(issues = ["PY-53104"])
  fun `typing Self in static method`() = test("""
    from __future__ import annotations
    from typing import Self

    class SomeClass:
        @staticmethod
        def foo(bar: Self) -> Self:
    #                │        ^^^^ WARNING Cannot use 'Self' in staticmethod
    #                ^^^^ WARNING Cannot use 'Self' in staticmethod
            return bar
    """)

  @Test
  @TestFor(issues = ["PY-53104"])
  fun `typing Self parameter has different annotation`() = test("""
    from __future__ import annotations
    from typing import Self

    class SomeClass:
        def foo(self: SomeClass, bar: Self) -> Self:
    #                                 │        ^^^^ WARNING Cannot use 'Self' if 'self' parameter is not 'Self' annotated
    #                                 ^^^^ WARNING Cannot use 'Self' if 'self' parameter is not 'Self' annotated
            return bar
    """)

  @Test
  @TestFor(issues = ["PY-53104"])
  fun `typing Self cls parameter has different annotation`() = test("""
    from __future__ import annotations
    from typing import Self

    class SomeClass:
        @classmethod
        def foo(cls: SomeClass, bar: Self) -> Self:
    #                                │        ^^^^ WARNING Cannot use 'Self' if 'cls' parameter is not 'Self' annotated
    #                                ^^^^ WARNING Cannot use 'Self' if 'cls' parameter is not 'Self' annotated
            return bar
    """)

  @Test
  @TestFor(issues = ["PY-53104"])
  fun `typing Self in static method body`() = test("""
    from typing import Self

    class C:
        @staticmethod
        def m():
            obj: Self
    #            ^^^^ WARNING Cannot use 'Self' in staticmethod
    """)

  @Test
  @TestFor(issues = ["PY-53104"])
  fun `typing Self in function body self parameter has different annotation`() = test("""
    from typing import Self

    class C:
        def m(self: C):
            obj: Self
    #            ^^^^ WARNING Cannot use 'Self' if 'self' parameter is not 'Self' annotated
    """)

  @Test
  @TestFor(issues = ["PY-62301"])
  fun `typing Self in new method`() = test("""
    from typing import Self

    class ReturnsSelf:
        def __new__(cls, value: int) -> Self: ...
    """)

  @Test
  @TestFor(issues = ["PY-36317"])
  fun `dict subscription not reported as parametrized generic`() = test(
    TestOptions(assertRecursionPrevention = false),
    """
    keys_and_types = {
        'comment': (str, type(None)),
        'from_budget': (bool, type(None)),
        'to_member': (int, type(None)),
        'survey_request': (int, type(None)),
    }

    def type_is_valid(test_key, test_value):
        return isinstance(test_value, keys_and_types[test_key])
    """)

  @Test
  @TestFor(issues = ["PY-36317"])
  fun `tuple subscription not reported as parametrized generic`() = test("""
    tuple_of_types = (str, bool, int)

    def my_is_instance(value, index: int) -> bool:
        return isinstance(value, tuple_of_types[index])
    """)

  @Test
  @TestFor(issues = ["PY-36317"])
  fun `plain dict type subscription not reported as parametrized generic`() = test("""
    def foo(d: dict, s: dict):
        for key in s.keys():
            if not isinstance(d[key], s[key]):
                raise TypeError
    """)

  @Test
  @TestFor(issues = ["PY-53105"])
  fun `no variadic Generic error in class declaration`() = test("""
    from typing import Generic, TypeVarTuple

    Shape = TypeVarTuple('Shape')

    class Array(Generic[*Shape]): ...
    """)

  @Test
  @TestFor(issues = ["PY-53105"])
  fun `TypeVarTuple name as Literal`() = test("""
    from typing import TypeVarTuple

    name = 'Ts'
    Ts = TypeVarTuple(name)
    #                 ^^^^ WARNING 'TypeVarTuple()' expects a string literal as first argument
    Ts1 = TypeVarTuple('Ts1')
    """)

  @Test
  @TestFor(issues = ["PY-53105"])
  fun `TypeVarTuple name and target name equality`() = test("""
    from typing import TypeVarTuple

    Ts = TypeVarTuple('T')
    #                 ^^^ WARNING The argument to 'TypeVarTuple()' must be a string equal to the variable name to which it is assigned
    Ts1 = TypeVarTuple('Ts1')
    """)

  @Test
  @TestFor(issues = ["PY-70528"])
  fun `TypeVarTuple from typing extensions name and target name equality`() = test("""
    from typing_extensions import TypeVarTuple

    Ts = TypeVarTuple('T')
    #                 ^^^ WARNING The argument to 'TypeVarTuple()' must be a string equal to the variable name to which it is assigned
    Ts1 = TypeVarTuple('Ts1')
    """)

  @Test
  @TestFor(issues = ["PY-53105"])
  fun `TypeVarTuple more than one unpacking`() = test("""
    from typing import TypeVarTuple
    from typing import Generic

    Ts1 = TypeVarTuple("Ts1")
    Ts2 = TypeVarTuple("Ts2")

    class Array(Generic[*Ts1, *Ts2]): ...
    #                         ^^^^ ERROR Parameters to generic cannot contain more than one unpacking
    """)

  @Test
  fun `TypeIs does not match`() = test("""
    from typing_extensions import TypeIs

    def foo(x: int) -> TypeIs[float]: ...
    #   ^^^ WARNING Return type of TypeIs 'float | int' is not consistent with the type of the first parameter 'int'

    def bar(x: float) -> TypeIs[float]: ...

    class A:
        def f1(self, x: int) -> TypeIs[float]: ...
    #       ^^ WARNING Return type of TypeIs 'float | int' is not consistent with the type of the first parameter 'int'

        def f2(self, x: float) -> TypeIs[float]: ...

        @classmethod
        def f3(cls, x: int) -> TypeIs[float]: ...
    #       ^^ WARNING Return type of TypeIs 'float | int' is not consistent with the type of the first parameter 'int'

        @classmethod
        def f4(cls, x: float) -> TypeIs[float]: ...

        @staticmethod
        def f5(x: int) -> TypeIs[float]: ...
    #       ^^ WARNING Return type of TypeIs 'float | int' is not consistent with the type of the first parameter 'int'

        @staticmethod
        def f6(x: float) -> TypeIs[float]: ...
    """)

  @Test
  fun `TypeIs does not match 2`() = test("""
    from typing_extensions import TypeIs

    class Base:
        pass

    class Derived(Base):
        pass

    def isInt123(x: Derived) -> TypeIs[Base]: ...
    #   ^^^^^^^^ WARNING Return type of TypeIs 'Base' is not consistent with the type of the first parameter 'Derived'
    """)

  @Test
  fun `TypeIs match`() = test("""
    from typing_extensions import TypeIs

    class Base:
        pass

    class Derived(Base):
        pass

    def isInt123(x: Base) -> TypeIs[Derived]: ...
    """)

  @Test
  fun `TypeIs missed parameter`() = test("""
    from typing_extensions import TypeIs

    def foo() -> TypeIs[float]: ...
    #   ^^^ WARNING User-defined TypeGuard or TypeIs functions must have at least one parameter

    class A:
      def foo(self) -> TypeIs[float]: ...
    #     ^^^ WARNING User-defined TypeGuard or TypeIs functions must have at least one parameter

      @classmethod
      def bar(cls) -> TypeIs[float]: ...
    #     ^^^ WARNING User-defined TypeGuard or TypeIs functions must have at least one parameter

      @staticmethod
      def buz() -> TypeIs[float]: ...
    #     ^^^ WARNING User-defined TypeGuard or TypeIs functions must have at least one parameter
    """)

  @Test
  fun `TypeGuard missed parameter`() = test("""
    from typing import TypeGuard

    def f1() -> TypeGuard[str]: ...
    #   ^^ WARNING User-defined TypeGuard or TypeIs functions must have at least one parameter

    def f2(x: bool) -> TypeGuard[str]: ...

    class A:
        def f1(self) -> TypeGuard[str]: ...
    #       ^^ WARNING User-defined TypeGuard or TypeIs functions must have at least one parameter

        def f2(self, x: int) -> TypeGuard[str]: ...

        @classmethod
        def f3(cls) -> TypeGuard[str]: ...
    #       ^^ WARNING User-defined TypeGuard or TypeIs functions must have at least one parameter

        @classmethod
        def f4(cls, x: float) -> TypeGuard[str]: ...

        @staticmethod
        def f5() -> TypeGuard[str]: ...
    #       ^^ WARNING User-defined TypeGuard or TypeIs functions must have at least one parameter

        @staticmethod
        def f6(x: bool) -> TypeGuard[str]: ...
    """)

  @Test
  @TestFor(issues = ["PY-71002"])
  fun `non-default TypeVars following ones with defaults`() = test("""
    from typing import TypeVar, Generic

    DefaultT = TypeVar("DefaultT", default = str)
    DefaultT1 = TypeVar("DefaultT1", default = int)
    NoDefaultT2 = TypeVar("NoDefaultT2")
    NoDefaultT3 = TypeVar("NoDefaultT3")

    class Clazz(Generic[DefaultT, DefaultT1, NoDefaultT2]): ...
    #                                        ^^^^^^^^^^^ ERROR Non-default TypeVars cannot follow ones with defaults
    class ClazzA(Generic[DefaultT1, NoDefaultT2]): ...
    #                               ^^^^^^^^^^^ ERROR Non-default TypeVars cannot follow ones with defaults
    class ClazzB(Generic[DefaultT, NoDefaultT2, DefaultT1]): ...
    #                              ^^^^^^^^^^^ ERROR Non-default TypeVars cannot follow ones with defaults
    class ClazzC(Generic[NoDefaultT2, NoDefaultT3]): ...
    """)

  @Test
  fun `cast call`() = test("""
    from typing import cast

    def f(val: object):
        v0 = cast(int, 10) # ok
        v1 = cast(list[int], val) # ok
        v2 = cast('list[float]', val) # ok
        v3 = cast(1, val)
    #             └ WARNING Expected a type
    """)

  @Test
  fun `isinstance and issubclass on NewType`() = test("""
    from typing import NewType

    UserId = NewType("UserId", int)

    def f(val):
        isinstance(val, UserId)
    #                   ^^^^^^ ERROR NewType type cannot be used with instance and class checks
        issubclass(int, UserId)
    #                   ^^^^^^ ERROR NewType type cannot be used with instance and class checks
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVar defaults scoping`() = test("""
    from typing import TypeVar, Generic

    S1 = TypeVar("S1")
    S2 = TypeVar("S2", default=S1)
    StepT = TypeVar("StepT", default=int | None)
    StartT = TypeVar("StartT", default="StopT")
    StopT = TypeVar("StopT", default=int)

    class slice(Generic[StartT, StopT, StepT]): ...
    #                   ^^^^^^ WARNING Default type of this type parameter refers to one or more type variables that are out of scope
    class slice2(Generic[StopT, StartT, StepT]): ...

    class Foo3(Generic[S1]):
        class Bar2(Generic[S2]): ...
    #                      ^^ WARNING Default type of this type parameter refers to one or more type variables that are out of scope
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `ParamSpec default scoping`() = test("""
    from typing import ParamSpec, Generic

    P1 = ParamSpec("P1", default=[int, str])
    P2 = ParamSpec("P2", default=P1)

    class Clazz(Generic[P2, P1]): ...
    #                   ^^ WARNING Default type of this type parameter refers to one or more type variables that are out of scope
    """)

  @Test
  @TestFor(issues = ["PY-71002"])
  fun `non-default ParamSpec following ones with defaults`() = test("""
    from typing import ParamSpec, Generic

    P1 = ParamSpec("P1")
    P2 = ParamSpec("P2", default=[int, str])

    class Clazz(Generic[P2, P1]): ...
    #                       ^^ ERROR Non-default TypeVars cannot follow ones with defaults
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVarTuple default scoping`() = test("""
    from typing import Generic, TypeVarTuple, Unpack

    Ts1 = TypeVarTuple("Ts1", default=Unpack[tuple[int, int]])
    Ts2 = TypeVarTuple("Ts2", default=Unpack[Ts1])

    class Clazz(Generic[*Ts2]): ...
    #                   ^^^^ WARNING Default type of this type parameter refers to one or more type variables that are out of scope
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVar allows default values`() = test("""
    from typing import TypeVar, Generic

    T = TypeVar("T", default=3)
    #                        └ WARNING Default type must be a type expression
    T1 = TypeVar("T1", default=True)
    #                          ^^^^ WARNING Default type must be a type expression
    T3 = TypeVar("T3", default="NormalT")
    NormalT = TypeVar("NormalT")
    T4 = TypeVar("T4", default=NormalT)
    T5 = TypeVar("T5", default=list)
    class Clazz: ...
    T6 = TypeVar("T6", default=Clazz)
    T7 = TypeVar("T7", default=[int])
    #                          ^^^^^ WARNING Default type must be a type expression
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `new-style TypeVar allows default values`() = test("""
    from typing import TypeVar, Generic, ParamSpec, TypeVarTuple

    T1 = TypeVar("T1")
    Ts1 = TypeVarTuple("Ts1")
    P1 = ParamSpec("P1")

    class Clazz1[T = int]: ...
    class Clazz2[T = dict[int, str]]: ...
    class Clazz3[T, T1 = T]: ...
    class Clazz4[T = 1]: ...
    #                └ WARNING Default type must be a type expression
    class Clazz5[T = True]: ...
    #                ^^^^ WARNING Default type must be a type expression
    class Clazz6[T = Ts1]: ...
    #                ^^^ WARNING 'TypeVarTuple' cannot be used in default type of TypeVar
    #                ^^^ WARNING Type variable 'Ts1' is out of scope
    class Clazz7[T = P1]: ...
    #                ^^ WARNING 'ParamSpec' cannot be used in default type of TypeVar
    #                ^^ WARNING Type variable 'P1' is out of scope
    class Clazz8[T = [int]]: ...
    #                ^^^^^ WARNING Default type must be a type expression
    """)

  @Test
  @TestFor(issues = ["PY-90365"])
  fun `class with list literal default used as another default`() = test("""
    class A[T = [int]]: ...
    #           ^^^^^ WARNING Default type must be a type expression
    class B[T = A]: ...
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `ParamSpec allows default values`() = test("""
    from typing import ParamSpec, TypeVar

    T = TypeVar("T1")
    #           ^^^^ WARNING The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned
    P = ParamSpec("P1")
    #             ^^^^ WARNING The argument to 'ParamSpec()' must be a string equal to the variable name to which it is assigned

    P1 = ParamSpec("P1", default=[])
    P2 = ParamSpec("P2", default=[int, str, None, int | None])
    P3 = ParamSpec("P3", default=[int, T])
    P4 = ParamSpec("P4", default=[int])
    P5 = ParamSpec("P5", default=...)
    P6 = ParamSpec("P6", default=int)
    #                            ^^^ WARNING Default type of ParamSpec must be a ParamSpec type or a list of types
    P7 = ParamSpec("P7", default=3)
    #                            └ WARNING Default type of ParamSpec must be a ParamSpec type or a list of types
    P8 = ParamSpec("P8", default=(1, int))
    #                            ^^^^^^^^ WARNING Default type of ParamSpec must be a ParamSpec type or a list of types
    P9 = ParamSpec("P9", default=P)
    P10 = ParamSpec("P10", default=[1, 2])
    #                               │  └ WARNING Default type must be a type expression
    #                               └ WARNING Default type must be a type expression
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `new-style ParamSpec allows default values`() = test("""
    from typing import TypeVar, Generic, ParamSpec, TypeVarTuple

    T1 = TypeVar("T1")
    Ts1 = TypeVarTuple("Ts1")
    P1 = ParamSpec("P1")

    class Clazz1[**P = []]: ...
    class Clazz2[**P = [int]]: ...
    class Clazz3[**P = [int, str]]: ...
    class Clazz4[**P = [int, 3]]: ...
    #                        └ WARNING Default type must be a type expression
    class Clazz5[**P = [int, True]]: ...
    #                        ^^^^ WARNING Default type must be a type expression
    class Clazz6[**P = True]: ...
    #                  ^^^^ WARNING Default type of ParamSpec must be a ParamSpec type or a list of types
    class Clazz7[**P = T1]: ...
    #                  ^^ WARNING Default type of ParamSpec must be a ParamSpec type or a list of types
    #                  ^^ WARNING Type variable 'T1' is out of scope
    class Clazz8[**P = Ts1]: ...
    #                  ^^^ WARNING Default type of ParamSpec must be a ParamSpec type or a list of types
    #                  ^^^ WARNING Type variable 'Ts1' is out of scope
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVarTuple allows default values`() = test("""
    from typing import TypeVarTuple, Unpack, TypeVar

    T = TypeVar("T")
    Ts0 = TypeVarTuple("Ts0")
    Ts1 = TypeVarTuple("Ts1", default=Unpack[tuple[int]])
    Ts2 = TypeVarTuple("Ts2", default=tuple[int])
    #                                 ^^^^^^^^^^ WARNING Default type of TypeVarTuple must be unpacked
    Ts3 = TypeVarTuple("Ts3", default=int)
    #                                 ^^^ WARNING Default type of TypeVarTuple must be unpacked
    Ts4 = TypeVarTuple("Ts4", default=Unpack[Ts0])
    Ts5 = TypeVarTuple("Ts5", default=Ts0)
    #                                 ^^^ WARNING Default type of TypeVarTuple must be unpacked
    Ts6 = TypeVarTuple("Ts6", default=Unpack[tuple[int, ...]])
    Ts7 = TypeVarTuple("Ts7", default=Unpack[tuple[T, T]])
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `new-style TypeVarTuple allows default values`() = test("""
    from typing import TypeVar, Generic, ParamSpec, TypeVarTuple, Unpack

    class Clazz1[T1, *Ts = Unpack[tuple[int, T1]]]: ...
    class Clazz2[*Ts = 1]: ...
    #                  └ WARNING Default type of TypeVarTuple must be unpacked
    class Clazz3[*Ts = True]: ...
    #                  ^^^^ WARNING Default type of TypeVarTuple must be unpacked
    class Clazz4[*Ts = tuple[int]]: ...
    #                  ^^^^^^^^^^ WARNING Default type of TypeVarTuple must be unpacked
    class Clazz5[*Ts = *tuple[int]]: ...
    class Clazz6[*Ts = Unpack[tuple[int]]]: ...
    class Clazz7[T1, *Ts = T1]: ...
    #                      ^^ WARNING Default type of TypeVarTuple must be unpacked
    class Clazz8[*Ts1, *Ts = Ts1]: ...
    #                        ^^^ WARNING Default type of TypeVarTuple must be unpacked
    class Clazz9[**P1, *Ts = P1]: ...
    #                        ^^ WARNING Default type of TypeVarTuple must be unpacked
    class Clazz10[*Ts = Unpack[tuple[int, ...]]]: ...
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVar can not follow TypeVarTuple`() = test("""
    from typing import TypeVar, Generic, ParamSpec, TypeVarTuple, Unpack

    T = TypeVar("T", default = int)
    Ts = TypeVarTuple("Ts")
    TsDef = TypeVarTuple("TsDef", default = Unpack[tuple[int, int]])
    P = ParamSpec("P", default = [str, bool])

    class Clazz(Generic[Ts, T]): ...
    #                       └ ERROR TypeVar with a default value cannot follow TypeVarTuple
    class Clazz1(Generic[TsDef, T]): ...
    #                           └ ERROR TypeVar with a default value cannot follow TypeVarTuple
    class Clazz2(Generic[TsDef, P]): ...
    class Clazz3(Generic[Ts, P]): ...
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVar parameterization with defaults`() = test("""
    from typing import TypeVar, Generic

    DefaultT = TypeVar("DefaultT", default = str)
    DefaultT1 = TypeVar("DefaultT1", default = int)
    NoDefaultT2 = TypeVar("NoDefaultT2")
    NoDefaultT3 = TypeVar("NoDefaultT3")
    NoDefaultT4 = TypeVar("NoDefaultT4")

    class Clazz(Generic[NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1]): ...

    c1 = Clazz[int]()
    #          ^^^ WARNING Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'
    c2 = Clazz[int, str]()
    #          ^^^^^^^^ WARNING Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'
    c3 = Clazz[int, str, bool]()
    c4 = Clazz[int, str, bool, int]()
    c5 = Clazz[int, str, bool, int, str]()
    c6 = Clazz[int, str, bool, int, str, int]()
    #          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'
    c7 = Clazz[int, str, bool, int, str, int, int]()
    #          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVar parameterization explicit Any in defaults`() = test("""
    from typing import Generic, TypeVar, Any

    T = TypeVar('T')
    T1 = TypeVar('T1', default=Any)
    T2 = TypeVar('T2', default=Any)

    class Clazz(Generic[T, T1, T2]): ...

    c = Clazz[int]()
    c1 = Clazz[int, str]()
    c2 = Clazz[int, str, bool]()
    c3 = Clazz[int, str, bool, float]()
    #          ^^^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters [T, T1, T2] of class 'Clazz'
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVarTuple parameterization with defaults`() = test("""
    from typing import TypeVar, Generic, TypeVarTuple, Unpack

    DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[int, str]])
    class Clazz(Generic[DefaultTs]): ...

    c1 = Clazz[int]()
    c2 = Clazz[int, str]()
    c3 = Clazz[int, str, bool]()
    c4 = Clazz[int, str, bool, int]()
    c5 = Clazz[int, str, bool, int, str]()
    c6 = Clazz[int, str, bool, int, str, int]()
    c7 = Clazz[int, str, bool, int, str, int, int]()
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `default ParamSpec following TypeVarTuple`() = test("""
    from typing import TypeVar, Generic, TypeVarTuple, ParamSpec, Unpack

    Ts = TypeVarTuple("Ts")
    P = ParamSpec("P", default=[float, bool])

    class Clazz(Generic[*Ts, P]): ...

    c1 = Clazz[int]()
    c2 = Clazz[int, str]()
    c3 = Clazz[int, str, [bool]]()
    c4 = Clazz[int, str, [bool, int]]()
    c5 = Clazz[int, str, [bool, int, str]]()
    c6 = Clazz[int, [str, bool, int, str, int]]()

    Ts1 = TypeVarTuple("Ts1", default=Unpack[tuple[int, str]])
    class Clazz1(Generic[*Ts, P]): ...
    c11 = Clazz1[int]()
    c12 = Clazz1[int, str]()
    c13 = Clazz1[int, str, [bool]]()
    c14 = Clazz1[int, str, [bool, int]]()
    c15 = Clazz1[int, str, [bool, int, str]]()
    c16 = Clazz1[int, [str, bool, int, str, int]]()
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVar and TypeVarTuple parameterization with defaults`() = test("""
    from typing import TypeVar, Generic, TypeVarTuple, Unpack

    DefaultT = TypeVar("DefaultT", default = str)
    DefaultT1 = TypeVar("DefaultT1", default = int)
    NoDefaultT2 = TypeVar("NoDefaultT2")
    NoDefaultT3 = TypeVar("NoDefaultT3")
    DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[int, str]])

    class Clazz(Generic[NoDefaultT2, NoDefaultT3, DefaultT, DefaultT1, DefaultTs]): ...

    c1 = Clazz[int]()
    #          ^^^ WARNING Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, DefaultT, DefaultT1, *DefaultTs] of class 'Clazz'
    c2 = Clazz[int, str]()
    c3 = Clazz[int, str, bool]()
    c4 = Clazz[int, str, bool, int]()
    c5 = Clazz[int, str, bool, int, str]()
    c6 = Clazz[int, str, bool, int, str, int]()
    c7 = Clazz[int, str, bool, int, str, int, int]()
    c8 = Clazz[int, str, bool, int, str, int, int, float]()
    c9 = Clazz[int, str, bool, int, str, int, int, float, list]()
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `non-default type variables following ones with defaults new-style`() = test("""
    class Clazz[NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT = int, DefaultT1 = str]: ...

    c1 = Clazz[int]()
    #          ^^^ WARNING Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'
    c2 = Clazz[int, str]()
    #          ^^^^^^^^ WARNING Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'
    c3 = Clazz[int, str, bool]()
    c4 = Clazz[int, str, bool, int]()
    c5 = Clazz[int, str, bool, int, str]()
    c6 = Clazz[int, str, bool, int, str, int]()
    #          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'
    c7 = Clazz[int, str, bool, int, str, int, int]()
    #          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `type parameters out of scope not reported multiple times`() = test("""
    from typing import TypeVar, Generic

    T1 = TypeVar('T1')
    T2 = TypeVar('T2')
    T3 = TypeVar('T3', default=T1 | T2)
    T4 = TypeVar('T4', default=T2)

    class Clazz1(Generic[T3]): ...
    #                    ^^ WARNING Default type of this type parameter refers to one or more type variables that are out of scope
    class Clazz2(Generic[T3, T4]): ...
    #                    │   ^^ WARNING Default type of this type parameter refers to one or more type variables that are out of scope
    #                    ^^ WARNING Default type of this type parameter refers to one or more type variables that are out of scope
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVar default types are TypeVars`() = test("""
    from typing import TypeVar, TypeVarTuple, ParamSpec

    type A1[**P, T = P] = tuple[P, T]  # false negative
    #                └ WARNING 'ParamSpec' cannot be used in default type of TypeVar

    Ts = TypeVarTuple("Ts")
    T1 = TypeVar("T1", default=Ts)
    #                          ^^ WARNING 'TypeVarTuple' cannot be used in default type of TypeVar

    P = ParamSpec("P")
    T2 = TypeVar("T2", default=P)
    #                          └ WARNING 'ParamSpec' cannot be used in default type of TypeVar
    T3 = TypeVar("T3", default=dict[str, Ts])
    #                               ^^^^^^^ WARNING Passed type arguments do not match type parameters [_KT, _VT] of class 'dict'
    T4 = TypeVar("T4", default=dict[list[P], str])
    #                                    └ WARNING Passed type arguments do not match type parameters [_T] of class 'list'
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `TypeVarTuple is not considered mandatory type parameter`() = test("""
    class Clazz[T1, T2, *Ts, T3]: ...

    c1 = Clazz[int]()
    #          ^^^ WARNING Passed type arguments do not match type parameters [T1, T2, *Ts, T3] of class 'Clazz'
    c2 = Clazz[int, str]()
    #          ^^^^^^^^ WARNING Passed type arguments do not match type parameters [T1, T2, *Ts, T3] of class 'Clazz'
    c3 = Clazz[int, str, bool]()
    c4 = Clazz[int, str, bool, float]()
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `allowed type arguments`() = test("""
    from typing import Literal, TypeAlias

    class Clazz[T1, T2 = int]: ...

    var = 1
    myInt = int
    type myIntOrStr = int | str
    myIntAlias: TypeAlias = int

    class A:...

    c1 = Clazz[print(), int]()
    #          ^^^^^^^ ERROR Invalid type argument
    c2 = Clazz[int, print()]()
    #               ^^^^^^^ ERROR Invalid type argument
    c3 = Clazz[1]
    #          └ ERROR Invalid type argument
    c4 = Clazz["int", "str"]
    c5 = Clazz[dict[int, str]]
    c7 = Clazz[True]
    #          ^^^^ ERROR Invalid type argument
    c8 = Clazz[list or set]
    #          ^^^^^^^^^^^ ERROR Invalid type argument
    c9 = Clazz[Literal[3]]
    c10 = Clazz[var]
    #           ^^^ ERROR Parameters to generic types must be types
    c11 = Clazz[myInt]
    c12 = Clazz[myIntOrStr]
    c13 = Clazz[myIntAlias]
    c14 = Clazz[A]
    c15 = Clazz[{"a": "b"}]
    #           ^^^^^^^^^^ ERROR Invalid type argument
    c16 = Clazz[(lambda: int)()]
    #           ^^^^^^^^^^^^^^^ ERROR Invalid type argument
    c17 = Clazz[(int, str)]
    """)

  @Test
  @TestFor(issues = ["PY-77601", "PY-76840"])
  fun `ParamSpec not mapped to single type without square brackets`() = test("""
    from typing import Generic, ParamSpec, TypeVar

    T = TypeVar("T")
    P1 = ParamSpec("P1")
    P2 = ParamSpec("P2")

    class ClassA(Generic[T, P1]): ...

    x: ClassA[int, int]
    #         ^^^^^^^^ WARNING Passed type arguments do not match type parameters [T, **P1] of class 'ClassA'
    x1: ClassA[int, [int]]
    """)

  @Test
  @TestFor(issues = ["PY-75759"])
  fun `ellipsis not reported`() = test("""
    from typing import Generic, ParamSpec, TypeVar, Callable

    T = TypeVar("T")
    P1 = ParamSpec("P1")

    class ClassA(Generic[T, P1]): ...

    def func23(x: ClassA[int, ...]) -> str:  # OK
        return ""
    """)

  @Test
  @TestFor(issues = ["PY-79693"])
  fun `Never and NoReturn not reported as invalid type args`() = test("""
    from typing import Never, NoReturn

    class ClassA:
       a: NoReturn
       b: list[NoReturn]
       c: Never
       d: list[Never]
    """)

  @Test
  @TestFor(issues = ["PY-76820"])
  fun `explicit generic TypeAlias parameterization number of type parameters one TypeVar`() = test("""
    from typing import TypeAlias, TypeVar

    T = TypeVar("T")

    alias: TypeAlias = list[T]
    alias2: TypeAlias = list[T] | set[T] | T

    a1: alias[int]
    a2: alias[int, str]
    #         ^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    a3: alias[int, str, bool]
    #         ^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    a4: alias[int, str, bool, int]
    #         ^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'

    a21: alias2[int]
    a22: alias2[int, str]
    #           ^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias2'
    a23: alias2[int, str, bool]
    #           ^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias2'
    a24: alias2[int, str, bool, int]
    #           ^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias2'
    """)

  @Test
  @TestFor(issues = ["PY-76820"])
  fun `explicit generic TypeAlias parameterization number of type parameters two TypeVars`() = test("""
    from typing import TypeAlias, TypeVar

    T = TypeVar("T")
    U = TypeVar("U")

    alias: TypeAlias = dict[T, U]
    a1: alias[int]
    #         ^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    a2: alias[int, str]
    a3: alias[int, str, bool]
    #         ^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    a4: alias[int, str, bool, int]
    #         ^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    """)

  @Test
  @TestFor(issues = ["PY-76820"])
  fun `explicit generic TypeAlias parameterization number of type parameters multiple TypeVars`() = test("""
    from typing import TypeAlias, TypeVar, Generic

    T = TypeVar("T")
    T1 = TypeVar("T1")
    T2 = TypeVar("T2")
    T3 = TypeVar("T3")

    class Clazz(Generic[T3]): ...

    alias: TypeAlias = dict[T, list[T1]] | T2 | Clazz[T3]

    a1: alias[int]
    #         ^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    a2: alias[int, str]
    #         ^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    a3: alias[int, str, bool]
    #         ^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    a4: alias[int, str, bool, int]
    a5: alias[int, str, bool, int, float]
    #         ^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    a5: alias[int, str, bool, int, float, str]
    #         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    """)

  @Test
  @TestFor(issues = ["PY-76820"])
  fun `explicit generic TypeAlias parameterization number of type parameters two TypeVars one has default`() = test("""
    from typing import TypeAlias, TypeVar

    T = TypeVar("T")
    U = TypeVar("U", default=str)

    alias: TypeAlias = dict[T, U]
    a1: alias[int]
    a2: alias[int, str]
    a3: alias[int, str, bool]
    #         ^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    a4: alias[int, str, bool, int]
    #         ^^^^^^^^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'alias'
    """)

  @Test
  @TestFor(issues = ["PY-76820"])
  fun `explicit TypeAlias parameterization conformance tests`() = test("""
    from typing import TypeAlias as TA
    from typing import TypeVar, Callable

    T = TypeVar("T")

    GoodTypeAlias2: TA = int | None
    GoodTypeAlias3: TA = list[GoodTypeAlias2]
    GoodTypeAlias4: TA = list[T]
    GoodTypeAlias8: TA = Callable[[int, T], T]

    p1: GoodTypeAlias2[int]
    #                  ^^^ WARNING Type alias is not generic or already specialized
    p2: GoodTypeAlias3[int]
    #                  ^^^ WARNING Type alias is not generic or already specialized
    p3: GoodTypeAlias4[int, int]
    #                  ^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'GoodTypeAlias4'
    p4: GoodTypeAlias8[int, int]
    #                  ^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'GoodTypeAlias8'

    ListAlias: TA = list
    ListOrSetAlias: TA = list | set

    x2: ListAlias[int]
    #             ^^^ WARNING Type alias is not generic or already specialized
    x4: ListOrSetAlias[int]
    #                  ^^^ WARNING Type alias is not generic or already specialized
    """)

  @Test
  @TestFor(issues = ["PY-76839"])
  fun `implicit type alias already parameterized`() = test("""
    alias = list
    alias2 = list[int]
    a1: alias[int]  # OK
    a2: alias2[int]
    #          ^^^ WARNING Type alias is not generic or already specialized
    """)

  @Test
  @TestFor(issues = ["PY-76820"])
  // The behavior for the explicit type aliases differs, see the test above
  fun `explicit TypeAlias already parameterized`() = test("""
    from typing import TypeAlias

    alias: TypeAlias = list
    alias2: TypeAlias = list[int]
    a1: alias[int]
    #         ^^^ WARNING Type alias is not generic or already specialized
    a2: alias2[int]
    #          ^^^ WARNING Type alias is not generic or already specialized
    """)

  @Test
  @TestFor(issues = ["PY-76839"])
  fun `implicit type alias parameterization union`() = test("""
    ListOrTupleAlias = list | tuple
    x: ListOrTupleAlias[int]
    #                   ^^^ WARNING Type alias is not generic or already specialized
    """)

  @Test
  @TestFor(issues = ["PY-76839"])
  fun `implicit type alias parameterization conformance tests`() = test("""
    from typing import TypeAlias as TA
    from typing import TypeVar, Callable

    T = TypeVar("T")

    GoodTypeAlias2 = int | None
    GoodTypeAlias3 = list[GoodTypeAlias2]
    GoodTypeAlias4 = list[T]
    GoodTypeAlias8 = Callable[[int, T], T]

    p1: GoodTypeAlias2[int]  # ISSUES *
    #                  ^^^ WARNING Type alias is not generic or already specialized
    p2: GoodTypeAlias3[int]
    #                  ^^^ WARNING Type alias is not generic or already specialized
    p3: GoodTypeAlias4[int, int]
    #                  ^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'GoodTypeAlias4'
    p4: GoodTypeAlias8[int, int]
    #                  ^^^^^^^^ WARNING Passed type arguments do not match type parameters of type alias 'GoodTypeAlias8'

    ListAlias = list
    ListOrSetAlias = list | set
    x1: list[str] = ListAlias()
    x2 = ListAlias[int]()
    x4: ListOrSetAlias[int]  # ISSUES *
    #                  ^^^ WARNING Type alias is not generic or already specialized
    """)

  @Test
  @TestFor(issues = ["PY-76820"])
  fun `explicit TypeAlias invalid values conformance tests`() = test(
    TestOptions(assertRecursionPrevention = false),
    """
    from typing import TypeAlias as TA

    var1 = 3

    def foo(): ...

    BadTypeAlias1: TA = eval("".join(map(chr, [105, 110, 116])))
    #                   │    │       ^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Generics should be specified through square brackets
    #                   │    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Generics should be specified through square brackets
    #                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias2: TA = [int, str]
    #                   ^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias3: TA = ((int, str),)
    #                   ^^^^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias4: TA = [int for i in range(1)]
    #                   │             ^^^^^^^^ WARNING Generics should be specified through square brackets
    #                   ^^^^^^^^^^^^^^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias5: TA = {"a": "b"}  # ISSUES *
    #                   ^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias6: TA = (lambda: int)()
    #                   ^^^^^^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias7: TA = [int][0]
    #                   ^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias8: TA = int if 1 < 3 else str
    #                   ^^^^^^^^^^^^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias9: TA = var1
    #                   ^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias10: TA = True
    #                    ^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias11: TA = 1
    #                    └ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias12: TA = list or set
    #                    ^^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias13: TA = f"{'int'}"
    #                    ^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias14: TA = f"int"  # ISSUES *
    #                    ^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias15: TA = u"int"  # ISSUES *
    #                    ^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias16: TA = b"int"  # ISSUES *
    #                    ^^^^^^ WARNING Assigned value of type alias must be a correct type
    BadTypeAlias17: TA = "foo()"
    #                    ^^^^^^^ WARNING Assigned value of type alias must be a correct type
    """)

  @Test
  @TestFor(issues = ["PY-76820"])
  fun `explicit TypeAlias valid values conformance tests`() = test("""
    from typing import TypeAlias as TA
    from typing import Any, Callable, Concatenate, Literal, ParamSpec, TypeVar, Union

    S = TypeVar("S")
    T = TypeVar("T")
    P = ParamSpec("P")
    R = TypeVar("R")

    GoodTypeAlias1: TA = Union[int, str]
    GoodTypeAlias2: TA = int | None
    GoodTypeAlias3: TA = list[GoodTypeAlias2]
    GoodTypeAlias4: TA = list[T]
    GoodTypeAlias5: TA = tuple[T, ...] | list[T]
    GoodTypeAlias6: TA = tuple[int, int, S, T]
    GoodTypeAlias7: TA = Callable[..., int]
    GoodTypeAlias8: TA = Callable[[int, T], T]
    GoodTypeAlias9: TA = Callable[Concatenate[int, P], R]
    GoodTypeAlias10: TA = Any
    GoodTypeAlias11: TA = GoodTypeAlias1 | GoodTypeAlias2 | list[GoodTypeAlias4[int]]
    GoodTypeAlias12: TA = Callable[P, None]
    GoodTypeAlias13: TA = "int | str"
    GoodTypeAlias14: TA = list["int | str"]
    GoodTypeAlias15: TA = Literal[3, 4, 5, None]
    GoodTypeAlias16: TA = "Callable[Concatenate[int, P], R]"
    """)

  @Test
  @TestFor(issues = ["PY-76820"])
  // Duplicates the test from conformance test suite but in multi-file context
  fun `explicit type aliases multi-file`() = test(
    """
    from util import *

    def bad_type_aliases(
        p1: BadTypeAlias1,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p2: BadTypeAlias2,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p3: BadTypeAlias3,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p4: BadTypeAlias4,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p5: BadTypeAlias5,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p6: BadTypeAlias6,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p7: BadTypeAlias7,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p8: BadTypeAlias8,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p9: BadTypeAlias9,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p10: BadTypeAlias10,
    #        ^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p11: BadTypeAlias11,
    #        ^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p12: BadTypeAlias12,
    #        ^^^^^^^^^^^^^^ WARNING Invalid type annotation
    ): pass

    def good_type_aliases(
        p1: GoodTypeAlias1,
        p2: GoodTypeAlias2,
        p3: GoodTypeAlias3,
        p4: GoodTypeAlias4[int],
        p5: GoodTypeAlias5[str],
        p6: GoodTypeAlias6[int, str],
        p7: GoodTypeAlias7,
        p8: GoodTypeAlias8[str],
        p9: GoodTypeAlias9[[str, str], None],
        p10: GoodTypeAlias10,
        p11: GoodTypeAlias11,
        p12: GoodTypeAlias12,
        p13: GoodTypeAlias13,
        p14: GoodTypeAlias14,
        p15: GoodTypeAlias15,
    ): pass
    """,
    "util.py" to """
      from typing import Any, Callable, Concatenate, Literal, ParamSpec, TypeVar, Union, \
          assert_type, Never
      from typing import TypeAlias as TA

      S = TypeVar("S")
      T = TypeVar("T")
      P = ParamSpec("P")
      R = TypeVar("R")

      GoodTypeAlias1: TA = Union[int, str]
      GoodTypeAlias2: TA = int | None
      GoodTypeAlias3: TA = list[GoodTypeAlias2]
      GoodTypeAlias4: TA = list[T]
      GoodTypeAlias5: TA = tuple[T, ...] | list[T]
      GoodTypeAlias6: TA = tuple[int, int, S, T]
      GoodTypeAlias7: TA = Callable[..., int]
      GoodTypeAlias8: TA = Callable[[int, T], T]
      GoodTypeAlias9: TA = Callable[Concatenate[int, P], R]
      GoodTypeAlias10: TA = Any
      GoodTypeAlias11: TA = GoodTypeAlias1 | GoodTypeAlias2 | list[GoodTypeAlias4[int]]
      GoodTypeAlias12: TA = Callable[P, None]
      GoodTypeAlias13: TA = "int | str"
      GoodTypeAlias14: TA = list["int | str"]
      GoodTypeAlias15: TA = Literal[3, 4, 5, None]

      var1 = 3
      BadTypeAlias1: TA = eval("".join(map(chr, [105, 110, 116])))  # E
      BadTypeAlias2: TA = [int, str]  # E
      BadTypeAlias3: TA = ((int, str),)  # E
      BadTypeAlias4: TA = [int for i in range(1)]  # E
      BadTypeAlias5: TA = {"a": "b"}  # E
      BadTypeAlias6: TA = (lambda: int)()  # E
      BadTypeAlias7: TA = [int][0]  # E
      BadTypeAlias8: TA = int if 1 < 3 else str  # E
      BadTypeAlias9: TA = var1  # E
      BadTypeAlias10: TA = True  # E
      BadTypeAlias11: TA = 1  # E
      BadTypeAlias12: TA = list or set  # E
      """,
  )

  @Test
  @TestFor(issues = ["PY-76839"])
  // Duplicates the test from conformance test suite but in multi-file context
  fun `implicit type aliases multi-file`() = test(
    """
    from util import *

    def bad_type_aliases(
        p1: BadTypeAlias1,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p2: BadTypeAlias2,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p3: BadTypeAlias3,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p4: BadTypeAlias4,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p5: BadTypeAlias5,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p6: BadTypeAlias6,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p7: BadTypeAlias7,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p8: BadTypeAlias8,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p9: BadTypeAlias9,
    #       ^^^^^^^^^^^^^ WARNING Invalid type annotation
        p10: BadTypeAlias10,
    #        ^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p11: BadTypeAlias11,
    #        ^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p12: BadTypeAlias12,
    #        ^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p13: BadTypeAlias13,
    #        ^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p14: BadTypeAlias14,
    #        ^^^^^^^^^^^^^^ WARNING Invalid type annotation
    ): pass

    def good_type_aliases(
        p1: GoodTypeAlias1,
        p2: GoodTypeAlias2,
        p3: GoodTypeAlias3,
        p4: GoodTypeAlias4[int],
        p5: GoodTypeAlias5[str],
        p6: GoodTypeAlias6[int, str],
        p7: GoodTypeAlias7,
        p8: GoodTypeAlias8[str],
        p9: GoodTypeAlias9[[str, str], None],
        p10: GoodTypeAlias10,
        p11: GoodTypeAlias11,
        p12: GoodTypeAlias12[bool],
        p13: GoodTypeAlias13
    ): pass
    """,
    "util.py" to """
      from typing import Any, Callable, Concatenate, ParamSpec, TypeVar, Union

      S = TypeVar("S")
      T = TypeVar("T")
      P = ParamSpec("P")
      R = TypeVar("R")

      TFloat = TypeVar("TFloat", bound=float)

      GoodTypeAlias1 = Union[int, str]
      GoodTypeAlias2 = int | None
      GoodTypeAlias3 = list[GoodTypeAlias2]
      GoodTypeAlias4 = list[T]
      GoodTypeAlias5 = tuple[T, ...] | list[T]
      GoodTypeAlias6 = tuple[int, int, S, T]
      GoodTypeAlias7 = Callable[..., int]
      GoodTypeAlias8 = Callable[[int, T], T]
      GoodTypeAlias9 = Callable[Concatenate[int, P], R]
      GoodTypeAlias10 = Any
      GoodTypeAlias11 = GoodTypeAlias1 | GoodTypeAlias2 | list[GoodTypeAlias4[int]]
      GoodTypeAlias12 = list[TFloat]
      GoodTypeAlias13 = Callable[P, None]

      var1 = 3
      BadTypeAlias1 = eval("".join(map(chr, [105, 110, 116])))
      BadTypeAlias2 = [int, str]
      BadTypeAlias3 = ((int, str),)
      BadTypeAlias4 = [int for i in range(1)]
      BadTypeAlias5 = {"a": "b"}
      BadTypeAlias6 = (lambda: int)()
      BadTypeAlias7 = [int][0]
      BadTypeAlias8 = int if 1 < 3 else str
      BadTypeAlias9 = var1
      BadTypeAlias10 = True
      BadTypeAlias11 = 1
      BadTypeAlias12 = list or set
      BadTypeAlias13 = f"int"
      BadTypeAlias14 = "int | str"
      """,
  )

  @Test
  @TestFor(issues = ["PY-76839"])
  fun `implicit type alias assignment chain`() = test("""
    a1 = 3
    a2 = a1
    a3 = a2
    def foo(p: a3): ...
    #          ^^ WARNING Invalid type annotation
    """)

  @Test
  fun `multi-line type hint`() = test("""
    value: $TRIPLE_QUOTE
        int |
        str |
        list[int]
    $TRIPLE_QUOTE
    """)

  @Test
  fun `ParamSpec args kwargs is valid`() = test("""
    from typing import ParamSpec, Protocol

    P = ParamSpec("P")
    class Proto4(Protocol[P]):
        def __call__(self, a: int, *args: P.args, **kwargs: P.kwargs) -> None: ...
    """)

  @Test
  @TestFor(issues = ["PY-76834"])
  fun `TypeExpr valid annotations conformance tests suite`() = test("""
    import types
    from abc import ABC
    from typing import Any, Callable, Tuple, Union

    class UserDefinedClass: ...
    class AbstractBaseClass(ABC): ...

    def valid_annotations(
        p1: int,
        p2: str,
        p3: bytes,
        p4: bytearray,
        p5: memoryview,
        p6: complex,
        p7: float,
        p8: bool,
        p9: object,
        p10: type,
        p11: types.ModuleType,
        p12: types.FunctionType,
        p13: types.BuiltinFunctionType,
        p14: UserDefinedClass,
        p15: AbstractBaseClass,
        p16: int,
        p17: Union[int, str],
        p18: None,
        p19: list,
        p20: list[int],
        p21: tuple,
        p22: Tuple[int, ...],
        p23: Tuple[int, int, str],
        p24: Callable[..., int],
        p25: Callable[[int, str], None],
        p26: Any,
    ): ...
    """)

  @Test
  @TestFor(issues = ["PY-76834"])
  fun `TypeExpr invalid annotations conformance tests suite`() = test(
    TestOptions(assertRecursionPrevention = false),
    """
    import types

    var1 = 3

    def invalid_annotations(
        p1: eval("".join(map(chr, [105, 110, 116]))),
    #       │    │       ^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Generics should be specified through square brackets
    #       │    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Generics should be specified through square brackets
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p2: [int, str],
    #       ^^^^^^^^^^ WARNING Invalid type annotation
        p3: (int, str),
    #       ^^^^^^^^^^ WARNING Invalid type annotation
        p4: [int for i in range(1)],
    #       │             ^^^^^^^^ WARNING Generics should be specified through square brackets
    #       ^^^^^^^^^^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p5: {},
    #       ^^ WARNING Invalid type annotation
        p6: (lambda: int)(),
    #       ^^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p7: [int][0],
    #       ^^^^^^^^ WARNING Invalid type annotation
        p8: int if 1 < 3 else str,
    #       ^^^^^^^^^^^^^^^^^^^^^ WARNING Invalid type annotation
        p9: var1,
    #       ^^^^ WARNING Invalid type annotation
        p10: True,
    #        ^^^^ WARNING Invalid type annotation
        p11: 1,
    #        └ WARNING Invalid type annotation
        p12: -1,
    #        ^^ WARNING Invalid type annotation
        p13: int or str,
    #        ^^^^^^^^^^ WARNING Invalid type annotation
        p14: f"int",
    #        ^^^^^^ WARNING Invalid type annotation
        p15: types,
    #        ^^^^^ WARNING Invalid type annotation
    ): ...
    """)

  @Test
  @TestFor(issues = ["PY-61787"])
  fun `Concatenate not reported in Callable arguments`() = test("""
    from typing import ParamSpec, Concatenate, Any, TypeVar, Callable

    P = ParamSpec('P')
    T = TypeVar('T')

    def changing_signature(f: Callable[P, T]) -> Callable[Concatenate[Any, P], T]:  # no warnings expected
        ...
    """)

  @Test
  fun `class is already parameterized`() = test("""
    from typing import Generic, TypeVar

    DefaultStrT = TypeVar("DefaultStrT", default=str)
    T = TypeVar("T")
    T1 = TypeVar("T1")

    class Base(Generic[T, T1, DefaultStrT]): ...
    class Foo(Base[int, float]): ...
    foo = Foo[int]()
    #         ^^^ WARNING Class 'Foo' is already parameterized

    class Bar(Generic[T]): ...
    class Baz(Bar[int]): ...
    baz = Baz[int]()
    #         ^^^ WARNING Class 'Baz' is already parameterized

    class NoErr(Bar[int]):
         def __class_getitem__(cls, item) -> str:
             return "str"

    n = NoErr[int]()
    """)

  @Test
  @TestFor(issues = ["PY-76894"])
  fun `raw Concatenate usage`() = test("""
    from typing import ParamSpec, Concatenate, Any, TypeVar, Callable, TypeAlias

    P = ParamSpec('P')
    T = TypeVar('T')

    # Raw Concatenate in function parameters is not allowed
    def func1(x: Concatenate[int, P]) -> int: ...
    #            ^^^^^^^^^^^^^^^^^^^ WARNING 'Concatenate' can only be used as the first argument to 'Callable' in this context

    var: Concatenate[int, P]
    #    │                └ WARNING Unbound type variable
    #    ^^^^^^^^^^^^^^^^^^^ WARNING 'Concatenate' can only be used as the first argument to 'Callable' in this context

    def return_concat() -> Concatenate[int, P]: ...
    #                      ^^^^^^^^^^^^^^^^^^^ WARNING 'Concatenate' can only be used as the first argument to 'Callable' in this context

    # Concatenate in type alias is allowed
    ConcatenateAlias = Concatenate[int, P]
    ConcatenateAlias2: TypeAlias = Concatenate[int, P]
    type ConcatenateAlias3[**P] = Concatenate[int, P]

    # Concatenate in Callable is allowed
    def changing_signature(f: Callable[P, T]) -> Callable[Concatenate[Any, P], T]: ...
    """)

  @Test
  @TestFor(issues = ["PY-80248"])
  fun `reference to type statement is valid type hint`() = test("""
    type my_type = str

    def func1(x: my_type) -> str: ...
    """)

  @Test
  @TestFor(issues = ["PY-80278"])
  fun `reference to namedtuple is valid type hint`() = test("""
    from collections import namedtuple

    Instruction = namedtuple("Instruction", ["register", "op", "value", "base", "check", "limit"])

    def foo() -> Instruction:  # No warning expected
        return Instruction(1, 2, 3, 4, 5, 6)
    """)

  @Test
  fun `unresolved reference not reported as invalid type argument`() = test("""
    from missing_module import SomeType  # ISSUES *

    def func4(some_type_tuple: tuple[SomeType, ...]):
        pass

    class Clazz[T, T1]: ...

    c = Clazz[RefToNoWhere, WrongRef]() # will be reported by PyUnresolvedReferencesInspection, but not here
    #         │             ^^^^^^^^ ERROR Unresolved reference 'WrongRef'
    #         ^^^^^^^^^^^^ ERROR Unresolved reference 'RefToNoWhere'
    """)

  @Test
  @TestFor(issues = ["PY-76862"])
  fun `unions in type annotations with multiple elements`() = test("""
    bad1: "ClassA" | int
    #     ^^^^^^^^ ERROR Union type annotations with forward references must be wrapped in quotes entirely
    bad2: int | "ClassA"
    #           ^^^^^^^^ ERROR Union type annotations with forward references must be wrapped in quotes entirely
    bad3: int | str | bool | "ClassA"
    #                        ^^^^^^^^ ERROR Union type annotations with forward references must be wrapped in quotes entirely
    bad4: int | "ClassA" | str | bool
    #           ^^^^^^^^ ERROR Union type annotations with forward references must be wrapped in quotes entirely
    good1: int | list["ClassA"]
    class ClassA: ...
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default can be subclass of bound`() = test("""
    from typing import TypeVar, List

    T1 = TypeVar('T1', bound=int, default=bool)
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default can not be subclass of constraint`() = test("""
    from typing import TypeVar, List

    T1 = TypeVar('T1', int, str, default=bool)
    #                                    ^^^^ WARNING Default type of TypeVar must be one of the constraint types
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default type matches constraints`() = test("""
    from typing import TypeVar, List

    # Default type matches one of the constraints
    T1 = TypeVar('T1', str, int, default=str)
    T2 = TypeVar('T2', str, int, default=int)

    # Default type doesn't match any of the constraints
    T3 = TypeVar('T3', str, int, default=bool)
    #                                    ^^^^ WARNING Default type of TypeVar must be one of the constraint types
    T4 = TypeVar('T4', str, int, default=List[int])
    #                                    ^^^^^^^^^ WARNING Default type of TypeVar must be one of the constraint types
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default type referring to TypeVar matches bound`() = test("""
    from typing import TypeVar

    Y1 = TypeVar("Y1", bound=int)
    Invalid = TypeVar("Invalid", float, str, default=Y1)
    #                                                ^^ WARNING Default type of TypeVar must be one of the constraint types
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default type referring to TypeVar matches constraints`() = test("""
    from typing import TypeVar

    Y1 = TypeVar("Y1", int, str)
    AlsoOk2 = TypeVar("AlsoOk2", int, str, bool, default=Y1)  # OK
    AlsoInvalid2 = TypeVar("AlsoInvalid2", bool, complex, default=Y1)
    #                                                             ^^ WARNING Default type of TypeVar must be one of the constraint types
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default type referring to TypeVar without constraints`() = test("""
    from typing import TypeVar

    T = TypeVar("T")
    Invalid = TypeVar("Invalid", str, int, default=T)
    #                                              └ WARNING Default type of TypeVar must be one of the constraint types
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default bound matched against bound`() = test("""
    from typing import TypeVar

    X1 = TypeVar("X1", bound=int)
    Ok1 = TypeVar("Ok1", default=X1, bound=float)
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default bound not matched against constraints`() = test("""
    from typing import TypeVar

    Y3 = TypeVar("Y3", bound=int)
    Invalid3 = TypeVar("Invalid3", str, complex, default=Y3)
    #                                                    ^^ WARNING Default type of TypeVar must be one of the constraint types
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default bound not matched against bound`() = test("""
    from typing import TypeVar

    X1 = TypeVar("X1", bound=int)
    Invalid1 = TypeVar("Invalid1", default=X1, bound=str)
    #                                      ^^ WARNING Default type of TypeVar is not a subtype of the bound
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default constraints not matched against bound`() = test("""
    from typing import TypeVar

    Y4 = TypeVar("Y4", int, str)
    Invalid4 = TypeVar("Invalid4", bound=str, default=Y4)
    #                                                 ^^ WARNING Default type of TypeVar is not a subtype of the bound
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default checks new syntax`() = test("""
    # NOT OK
    def foo1[T1: int = str](): ...
    #                  ^^^ WARNING Default type of TypeVar is not a subtype of the bound
    def foo2[T1: (int, bool) = str](): ...
    #                          ^^^ WARNING Default type of TypeVar must be one of the constraint types
    def foo3[T1: int, T2: str = T1](): ...
    #                           ^^ WARNING Default type of TypeVar is not a subtype of the bound
    def foo4[T1: (int, bool), T2: str = T1](): ...
    #                                   ^^ WARNING Default type of TypeVar is not a subtype of the bound
    def foo5[T1: (int, bool), T2: (int, str) = T1](): ...
    #                                          ^^ WARNING Default type of TypeVar must be one of the constraint types
    def foo6[T1: (int, str, float), T2: (int, float) = T1](): ...
    #                                                  ^^ WARNING Default type of TypeVar must be one of the constraint types
    def foo7[T1: (int, str) = bool](): ...
    #                         ^^^^ WARNING Default type of TypeVar must be one of the constraint types

    # OK
    def bar1[T1: int = bool](): ...
    def bar2[T1: (int, str) = str](): ...
    def bar3[T1: bool, T2: int = T1](): ...
    def bar4[T1: (int, str), T2: (str, int) = T1](): ...
    def bar5[T1: (int, str), T2: (str, int, float) = T1](): ...
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default type matched with object`() = test("""
    from typing import TypeVar, Any

    T = TypeVar('T')
    T1 = TypeVar('T1', bound=object, default=T)
    T2 = TypeVar('T2', int, object, default=T)
    """)

  @Test
  @TestFor(issues = ["PY-76870"])
  fun `TypeVar default Any in constraints and bound`() = test("""
    from typing import TypeVar, Any

    T1 = TypeVar("T1", int, str)
    Ok1 = TypeVar("Ok1", int, str, Any, default=T1)
    T2 = TypeVar("T2", int, str, Any)
    Ok2 = TypeVar("Ok2", int, str, Any, default=T2)
    T3 = TypeVar("T3", bound=Any)
    Ok3 = TypeVar("Ok3", bound=Any, default=T3)
    T4 = TypeVar("T4", bound=str)
    Ok4 = TypeVar("Ok4", bound=Any, default=T4)
    T5 = TypeVar("T5", bound=Any)
    Ok5 = TypeVar("Ok5", bound=Any, default=T5)

    Y1 = TypeVar("Y1", int, str, Any)
    NotOk1 = TypeVar("NotOk1", int, str, default=Y1)
    #                                            ^^ WARNING Default type of TypeVar must be one of the constraint types
    Y2 = TypeVar("Y2", bound=Any)
    NotOk2 = TypeVar("NotOk2", int, str, default=Y2)
    #                                            ^^ WARNING Default type of TypeVar must be one of the constraint types
    Y3 = TypeVar("Y3", bound=str)
    NotOk3 = TypeVar("NotOk3", int, Any, default=Y3)
    #                                            ^^ WARNING Default type of TypeVar must be one of the constraint types
    Y4 = TypeVar("Y4", str, Any)
    NotOk4 = TypeVar("NotOk4", int, Any, default=Y4)
    #                                            ^^ WARNING Default type of TypeVar must be one of the constraint types
    Y5 = TypeVar("Y5", bound=Any)
    NotOk5 = TypeVar("NotOk5", int, str, Any, default=Y5)
    #                                                 ^^ WARNING Default type of TypeVar must be one of the constraint types
    """)

  @Test
  @TestFor(issues = ["PY-76852"])
  fun `two unpacked unbound tuples`() = test("""
    from typing import TypeVarTuple, Unpack

    t1: tuple[*tuple[str, ...], *tuple[int, ...]]
    #   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple
    t2: tuple[*tuple[str, *tuple[str, ...]], *tuple[int, ...]]
    #   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple
    t3: tuple[Unpack[tuple[str, ...]], Unpack[tuple[int, ...]]]
    #   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple
    t4: tuple[Unpack[tuple[str, Unpack[tuple[str, ...]]]], Unpack[tuple[int, ...]]]
    #   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple

    # > An unpacked TypeVarTuple counts as an unbounded tuple in the context of this rule

    Ts = TypeVarTuple("Ts")

    def func(t: tuple[*Ts]):
        t5: tuple[*tuple[str], *Ts]
        t6: tuple[*tuple[str, ...], *Ts]
    #       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple
    """)

  @Test
  @TestFor(issues = ["PY-76862"])
  fun `check circular references`() = test(
    TestOptions(assertRecursionPrevention = false),
    """
    from typing import TypeAlias

    class ClassA: ...

    type ClassB = str

    ClassC = int

    ClassD: TypeAlias = bool

    circular: "circular" = None
    #         ^^^^^^^^^^ ERROR Circular reference

    class Test:
        ClassA: "ClassA"  # OK
        ClassB: "ClassB"  # OK
        ClassC: "ClassC"  # OK
        ClassD: "ClassD"  # OK

        ClassE: "ClassE"  # E: circular reference
    #           ^^^^^^^^ ERROR Circular reference

        ClassG: "ClassG" = None  # E: circular reference
    #           ^^^^^^^^ ERROR Circular reference

        def foo(self):
           Test: "Test"
           ClassA: "ClassA"  # OK
           ClassB: "ClassB"  # OK
           ClassC: "ClassC"  # OK
           str: "str"  # OK
           def int(self) -> None: ...
           x: "int" = 0 # OK
           var: "var" = None  # E: circular reference
    #           ^^^^^ ERROR Circular reference
    """)

  @Test
  fun `Concatenate not reported as illegal first param`() = test("""
    from typing import Callable, Concatenate

    x: Callable[Concatenate[int, ...], str]
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `invalid type alias statement`() = test(
    TestOptions(assertRecursionPrevention = false),
    """
    var1 = 1
    type BadTypeAlias1 = eval("".join(map(chr, [105, 110, 116])))
    #                    │    │       ^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Generics should be specified through square brackets
    #                    │    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Generics should be specified through square brackets
    #                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING Invalid type annotation
    type BadTypeAlias2 = [int, str]
    #                    ^^^^^^^^^^ WARNING Invalid type annotation
    type BadTypeAlias3 = ((int, str),)
    #                     ^^^^^^^^^^^ WARNING Invalid type annotation
    type BadTypeAlias4 = [int for i in range(1)]
    #                    │             ^^^^^^^^ WARNING Generics should be specified through square brackets
    #                    ^^^^^^^^^^^^^^^^^^^^^^^ WARNING Invalid type annotation
    type BadTypeAlias5 = {"a": "b"}
    #                    ^^^^^^^^^^ WARNING Invalid type annotation
    type BadTypeAlias6 = (lambda: int)()
    #                    ^^^^^^^^^^^^^^^ WARNING Invalid type annotation
    type BadTypeAlias7 = [int][0]
    #                    ^^^^^^^^ WARNING Invalid type annotation
    type BadTypeAlias8 = int if 1 < 3 else str
    #                    ^^^^^^^^^^^^^^^^^^^^^ WARNING Invalid type annotation
    type BadTypeAlias9 = var1
    #                    ^^^^ WARNING Invalid type annotation
    type BadTypeAlias10 = True
    #                     ^^^^ WARNING Invalid type annotation
    type BadTypeAlias11 = 1
    #                     └ WARNING Invalid type annotation
    type BadTypeAlias12 = list or set
    #                     ^^^^^^^^^^^ WARNING Invalid type annotation
    type BadTypeAlias13 = f"{'int'}"
    #                     ^^^^^^^^^^ WARNING Invalid type annotation
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `type alias statement scope`() = test("""
    type B = int
    class C:
        type D = int
    def func():
        type A = int
    #   ^^^^^^^^^^^^ WARNING A 'type' statement can be used only within a module or class scope
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `type alias bound match`() = test("""
    type TypeAlias[S: str] = list[S]
    r: TypeAlias[str] = [""]
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `type alias bound mismatch`() = test("""
    type TypeAlias[S: int] = list[S]
    r: TypeAlias[str] = [""]
    #            ^^^ WARNING Expected type 'S ≤: int', got 'str' instead
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `type alias old-style bound mismatch`() = test("""
    from typing import TypeAlias, TypeVar

    T = TypeVar("T", bound=str)
    Alias: TypeAlias = list[T]
    x: Alias[int]
    #        ^^^ WARNING Expected type 'T ≤: str', got 'int' instead
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `class variadic type parameters`() = test("""
    from typing import Callable

    class A[S1, **S2]:
        t: Callable[S2, S1]

    a: A[int, ...]
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `class bound mismatch`() = test("""
    class C[T: str]: ...
    c = C[int]()
    #     ^^^ WARNING Expected type 'T ≤: str', got 'int' instead
    """)

  @Test
  @TestFor(issues = ["PY-88277"])
  fun `class TypeVarTuple bound mismatch`() = test("""
    from typing import Unpack

    class C[*Ts: str]: ...
    #            ^^^ ERROR Type variable tuples cannot have constraints or upper bounds
    c1 = C[str, str]()
    c2 = C[str, int]()
    #      ^^^^^^^^ WARNING Expected type '*Ts ≤: str', got '*tuple[str, int]' instead
    c3 = C[int, str]()
    #      ^^^^^^^^ WARNING Expected type '*Ts ≤: str', got '*tuple[int, str]' instead

    class D[*Ts: Unpack[tuple[str]]]: ...
    #            ^^^^^^^^^^^^^^^^^^ ERROR Type variable tuples cannot have constraints or upper bounds
    d1 = D[str]()
    d2 = D[str, str]()
    #      ^^^^^^^^ WARNING Expected type '*Ts ≤: *tuple[str]', got '*tuple[str, str]' instead
    d3 = D[int, str]()
    #      ^^^^^^^^ WARNING Expected type '*Ts ≤: *tuple[str]', got '*tuple[int, str]' instead
    """)

  @Test
  @TestFor(issues = ["PY-88277"])
  fun `class ParamSpec bound mismatch`() = test("""
    class C[**P: [str]]: ...
    #            ^^^^^ ERROR Parameter specifications cannot have constraints or upper bounds
    c = C[int]()
    #     ^^^ WARNING Expected type '**P ≤: [str]', got '[int]' instead
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `type alias variadic type parameters`() = test("""
    from typing import Callable

    type TypeAlias[S1, **S2] = Callable[S2, S1]
    type TypeAlias2 = TypeAlias[int, ...]
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `simple recursive type alias statement`() = test("""
    type TypeAlias = TypeAlias
    #                ^^^^^^^^^ WARNING Invalid type annotation
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `recursive type alias statement in union`() = test("""
    type TypeAlias = int | str | TypeAlias
    #                            ^^^^^^^^^ WARNING Circular reference
    type TypeAlias2 = int | str
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `union recursive type alias statement`() = test("""
    type TypeAlias = TypeAlias | int
    #                ^^^^^^^^^ WARNING Circular reference
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `deep recursive type alias statement`() = test("""
    type TypeAlias1 = TypeAlias2
    #                 ^^^^^^^^^^ WARNING Invalid type annotation
    type TypeAlias2 = TypeAlias3
    #                 ^^^^^^^^^^ WARNING Invalid type annotation
    type TypeAlias3 = TypeAlias1
    #                 ^^^^^^^^^^ WARNING Invalid type annotation
    """)

  @Test
  @TestFor(issues = ["PY-76851"])
  fun `correct recursive type alias statement`() = test("""
    type TypeAlias1 = list[TypeAlias1]
    """)

  @Test
  @TestFor(issues = ["PY-82979"])
  fun `implicit type alias using Literal multi-file`() = test(
    """
    from sample import HttpOk, Http400, Http404

    def foo() -> HttpOk[None] | Http400 | Http404:
        pass
    """,
    "sample.py" to """
      from typing import Literal, TypeVar

      Code = TypeVar("Code", bound=int)
      Response = TypeVar("Response", bound=list | str | None)
      Error = TypeVar("Error", default=str, bound= str | None)

      Http = tuple[Code, Response]
      HttpOk = Http[Literal[200], Response]
      Http400 = Http[Literal[400], Error]
      Http401 = Http[Literal[401], Error]
      Http403 = Http[Literal[403], Error]
      Http404 = Http[Literal[404], Error]
      Http422 = Http[Literal[422], ErrorResponse[list[ErrorDetails]]]
      Http500 = Http[Literal[500], Literal["Internal Server Error"]]
      """,
  )

  @Test
  @TestFor(issues = ["PY-81028"])
  fun `implicit type alias using Annotated multi-file`() = test(
    """
    from m import StrictStr

    s: StrictStr
    """,
    "m.py" to """
      from typing import Annotated

      StrictStr = Annotated[str, object()]
      """,
  )

  @Test
  @TestFor(issues = ["PY-83583"])
  fun `implicit type alias using Annotated with new-style union`() = test("""
    from typing import Annotated
    from typing_extensions import Doc

    class A:
        pass

    class B:
        pass

    AOrB = Annotated[
        A | B,
        Doc("An instance of either A or B"),
    ]

    a_or_b: AOrB
    """)

  @Test
  @TestFor(issues = ["PY-83699"])
  fun `implicit type alias using Callable with new-style union`() = test("""
    from typing import Awaitable, Any, Callable

    AsyncFunc = Callable[[int], Awaitable[Any | None]]

    f: AsyncFunc
    """)

  @Test
  @TestFor(issues = ["PY-83700"])
  fun `implicit type alias at class level multi-file`() = test(
    """
    from lib import Baz

    def bar(baz: Baz.SOME_TYPE) -> None: ...
    """,
    "lib.py" to """
      from typing import Generic, TypeVar

      T1 = TypeVar("T1")

      class Foo(Generic[T1]): ...

      class Baz:
          SOME_TYPE = Foo[int]
      """,
  )

  @Test
  @TestFor(issues = ["PY-81926"])
  fun `union with class overriding dunder OR`() = test("""
    from typing import Self

    class Cls:
        def __or__(self, other: Self) -> Self:
            return self

    def foo(arg: Cls | None) -> None:
        print(arg)
    """)

  @Test
  @TestFor(issues = ["PY-81439"])
  fun `implicit type alias using Literal`() = test("""
    from typing import Literal, TypeAlias, reveal_type

    A = Literal[1, 2]
    A1: TypeAlias = Literal[6, 7]
    type A2 = Literal[6, 7]
    A3 = Literal[666]
    B = Literal[False, True]
    C = Literal['A', 'B']

    def f(a: A, a1: A1, a2: A2, a3: A3, b: B, c: C) -> None:
        print(a, a1, a2, a3, b, c)
    """)

  @Test
  fun `Self imported from non-excluded typing extensions multi-file`() = test(
    """
    from typing_extensions import Self

    class C:
        def identity(self) -> Self:
            return self
    """,
    "typing_extensions.py" to """
      import typing

      if hasattr(typing, "Self"):  # 3.11+
          Self = typing.Self
      else:
          @_SpecialForm
          def Self(self, params):
              ${TRIPLE_QUOTE}Used to spell the type of "self" in classes.

              Example::

                from typing import Self

                class ReturnsSelf:
                    def parse(self, data: bytes) -> Self:
                        return self

              $TRIPLE_QUOTE

              raise TypeError(f"{self} is not subscriptable")
      """,
  )

  @Test
  @TestFor(issues = ["PY-76832"])
  fun `type Self as type arg`() = test("""
    from typing import TypeAlias, Self

    TupleSelf: TypeAlias = tuple[Self]  # E
    #                            ^^^^ WARNING Cannot use 'Self' outside class
    class A[T]: ...
    a = A[Self]()  # E
    #     ^^^^ WARNING Cannot use 'Self' outside class
    class B:
       def __init__(self):
           self.l: list[Self]  # OK
    """)

  @Test
  @TestFor(issues = ["PY-76832"])
  fun `type Self in base class type args`() = test("""
    from typing import Self

    class Bar[T]: ...
    class Baz(Bar[Self]): ...
    #             ^^^^ WARNING Cannot use 'Self' in this context
    """)

  @Test
  @TestFor(issues = ["PY-76832"])
  fun `type Self in metaclass`() = test("""
    from typing import Self, Any

    class MyMetaclass(type):
        def __new__(cls, *args: Any) -> Self: ...
    #                                   ^^^^ WARNING Type 'Self' cannot be used in a metaclass

        def __mul__(cls, count: int) -> list[Self]: ...
    #                                        ^^^^ WARNING Type 'Self' cannot be used in a metaclass
    """)

  @Test
  fun `subscription parentheses flattening`() = test("""
    from typing import List, Set, Dict, Tuple, Union, Optional

    class C1[T]: ...
    class C2[T1, T2]: ...

    a: list[((int))]
    #\ TYPE list[int]
    a: List[((int))]
    #\ TYPE list[int]

    a: list[((int, int))]
    #│        ^^^^^^^^ WARNING Passed type arguments do not match type parameters [_T] of class 'list'
    #\ TYPE list[int, int] FIXME list[Unknown]
    a: List[((int, int))]
    #│        ^^^^^^^^ WARNING Passed type arguments do not match type parameters [_T] of class 'list'
    #\ TYPE list[int, int] FIXME list[Unknown]

    a: tuple[((int)), (((str)))]
    #\ TYPE tuple[int, str]
    a: Tuple[((int)), (((str)))]
    #\ TYPE tuple[int, str]

    a: tuple[((int, int))]
    #\ TYPE tuple[int, int]
    a: Tuple[((int, int))]
    #\ TYPE tuple[int, int]

    a: tuple[((int, int)), int]
    #│       ^^^^^^^^^^^^ ERROR Invalid type argument
    #\ TYPE tuple[Unknown, int]
    a: Tuple[((int, int)), int]
    #│       ^^^^^^^^^^^^ ERROR Invalid type argument
    #\ TYPE tuple[Unknown, int]

    a: set[((int))]
    #\ TYPE set[int]
    a: Set[((int))]
    #\ TYPE set[int]

    a: set[((int, int))]
    #│       ^^^^^^^^ WARNING Passed type arguments do not match type parameters [_T] of class 'set'
    #\ TYPE set[int, int] FIXME set[Unknown]
    a: Set[((int, int))]
    #│       ^^^^^^^^ WARNING Passed type arguments do not match type parameters [_T] of class 'set'
    #\ TYPE set[int, int] FIXME set[Unknown]

    a: dict[((int)), (((str)))]
    #\ TYPE dict[int, str]
    a: Dict[((int)), (((str)))]
    #\ TYPE dict[int, str]

    a: dict[((int))]
    #│        ^^^ WARNING Passed type arguments do not match type parameters [_KT, _VT] of class 'dict'
    #\ TYPE dict[int] FIXME dict[Unknown, Unknown]
    a: Dict[((int))]
    #│        ^^^ WARNING Passed type arguments do not match type parameters [_KT, _VT] of class 'dict'
    #\ TYPE dict[int] FIXME dict[Unknown, Unknown]

    a: Union[((int, (((str)))))]
    #\ TYPE int | str

    a: Union[((int, int)), (int, int)]
    #│       │             ^^^^^^^^^^ ERROR Invalid type argument
    #│       ^^^^^^^^^^^^ ERROR Invalid type argument
    #\ TYPE Unknown

    a: Optional[((int))]
    #\ TYPE int | None

    a: Optional[((int, int))]
    #│            ^^^^^^^^ ERROR 'Optional' must have exactly one argument
    #\ TYPE Unknown FIXME Unknown | None

    a: tuple[((int)), ((...))]
    #\ TYPE tuple[int, ...]
    a: Tuple[((int)), ((...))]
    #\ TYPE tuple[int, ...]

    a: tuple[((...)), ((int))]
    #│         ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...]
    a: Tuple[((...)), ((int))]
    #│         ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...]

    a: tuple[(int,), ...]
    #│       ^^^^^^ ERROR Invalid type argument
    #\ TYPE tuple[Unknown, ...]
    a: Tuple[(int,), ...]
    #│       ^^^^^^ ERROR Invalid type argument
    #\ TYPE tuple[Unknown, ...]

    a: C1[(((int)))]
    #\ TYPE C1[int]

    a: C2[(((int), (str)))]
    #\ TYPE C2[int, str]
    """)

  @Test
  fun `subscription empty parentheses`() = test("""
    from typing import List, Set, Dict, Tuple, Union, Optional

    class C1[T]: ...

    a: tuple[()]
    #\ TYPE tuple[()]
    a: Tuple[()]
    #\ TYPE tuple[()]

    a: tuple[int, ()]
    #│            ^^ ERROR Empty tuple is allowed only as a sole argument
    #\ TYPE tuple[()] FIXME tuple[Unknown]
    a: Tuple[int, ()]
    #│            ^^ ERROR Empty tuple is allowed only as a sole argument
    #\ TYPE tuple[()] FIXME tuple[Unknown]

    a: tuple[(), int]
    #│       ^^ ERROR Empty tuple is allowed only as a sole argument
    #\ TYPE tuple[()] FIXME tuple[Unknown]
    a: Tuple[(), int]
    #│       ^^ ERROR Empty tuple is allowed only as a sole argument
    #\ TYPE tuple[()] FIXME tuple[Unknown]

    a: tuple[(), ...]
    #│       ^^ ERROR Empty tuple is allowed only as a sole argument
    #\ TYPE tuple[Unknown, ...]
    a: Tuple[(), ...]
    #│       ^^ ERROR Empty tuple is allowed only as a sole argument
    #\ TYPE tuple[Unknown, ...]

    a: tuple[(), ()]
    #│       │   ^^ ERROR Empty tuple is allowed only as a sole argument
    #│       ^^ ERROR Empty tuple is allowed only as a sole argument
    #\ TYPE tuple[Unknown, ...] FIXME tuple[Unknown]
    a: Tuple[(), ()]
    #│       │   ^^ ERROR Empty tuple is allowed only as a sole argument
    #│       ^^ ERROR Empty tuple is allowed only as a sole argument
    #\ TYPE tuple[Unknown, ...] FIXME tuple[Unknown]

    a: list[()]
    #│      ^^ WARNING Passed type arguments do not match type parameters [_T] of class 'list'
    #\ TYPE list FIXME list[Unknown]
    a: List[()]
    #│      ^^ WARNING Passed type arguments do not match type parameters [_T] of class 'list'
    #\ TYPE list FIXME list[Unknown]

    a: set[()]
    #│     ^^ WARNING Passed type arguments do not match type parameters [_T] of class 'set'
    #\ TYPE set FIXME set[Unknown]
    a: Set[()]
    #│     ^^ WARNING Passed type arguments do not match type parameters [_T] of class 'set'
    #\ TYPE set FIXME set[Unknown]

    a: dict[(), ()]
    #│      │   ^^ ERROR Invalid type argument
    #│      ^^ ERROR Invalid type argument
    #\ TYPE dict[Unknown, Unknown]
    a: Dict[(), ()]
    #│      │   ^^ ERROR Invalid type argument
    #│      ^^ ERROR Invalid type argument
    #\ TYPE dict[Unknown, Unknown]

    a: Union[()]
    #\ TYPE Never

    a: Optional[()]
    #│          ^^ ERROR 'Optional' must have exactly one argument
    #\ TYPE Unknown FIXME Unknown | None

    a: C1[()]
    #│    ^^ WARNING Passed type arguments do not match type parameters [T] of class 'C1'
    #\ TYPE C1 FIXME C1[Unknown]
    """)

  @Test
  fun `subscription type form`() = test("""
    from typing import TypeVar, List, Set, Dict, Tuple, Union, Optional, Callable, Annotated

    TV = TypeVar("TV")

    class C1[T]: ...

    a: list[((int,))]
    #\ TYPE list[int]
    a: List[((int,))]
    #\ TYPE list[int]

    a: tuple[((int,))]
    #\ TYPE tuple[int]
    a: Tuple[((int,))]
    #\ TYPE tuple[int]

    a: tuple[(int,), int]
    #│       ^^^^^^ ERROR Invalid type argument
    #\ TYPE tuple[Unknown, int]
    a: Tuple[(int,), int]
    #│       ^^^^^^ ERROR Invalid type argument
    #\ TYPE tuple[Unknown, int]

    a: tuple[(int, str), (int, str)]
    #│       │           ^^^^^^^^^^ ERROR Invalid type argument
    #│       ^^^^^^^^^^ ERROR Invalid type argument
    #\ TYPE tuple[Unknown, Unknown]
    a: Tuple[(int, str), (int, str)]
    #│       │           ^^^^^^^^^^ ERROR Invalid type argument
    #│       ^^^^^^^^^^ ERROR Invalid type argument
    #\ TYPE tuple[Unknown, Unknown]

    a: set[((int,))]
    #\ TYPE set[int]
    a: Set[((int,))]
    #\ TYPE set[int]

    a: dict[((int,)), str]
    #│      ^^^^^^^^ ERROR Invalid type argument
    #\ TYPE dict[Unknown, str]
    a: Dict[((int,)), str]
    #│      ^^^^^^^^ ERROR Invalid type argument
    #\ TYPE dict[Unknown, str]

    a: dict[int, (int, str)]
    #│           ^^^^^^^^^^ ERROR Invalid type argument
    #\ TYPE dict[int, Unknown]
    a: Dict[int, (int, str)]
    #│           ^^^^^^^^^^ ERROR Invalid type argument
    #\ TYPE dict[int, Unknown]

    a: dict[(([int]))]
    #│        ^^^^^ WARNING Passed type arguments do not match type parameters [_KT, _VT] of class 'dict'
    #\ TYPE dict[[int]] FIXME dict[Unknown, Unknown]
    a: Dict[(([int]))]
    #│        ^^^^^ ERROR Parameters to generic types must be types
    #\ TYPE dict[[int]] FIXME dict[Unknown, Unknown]

    a: tuple[Tuple[int, str]]
    #\ TYPE tuple[tuple[int, str]]

    a: Tuple[tuple[int], ...]
    #\ TYPE tuple[tuple[int], ...]

    a: tuple[*Tuple[*tuple[int]]]
    #\ TYPE tuple[int]

    a: tuple[int, *Tuple[*Tuple[int, str]], str]
    #\ TYPE tuple[int, int, str, str]

    a: tuple[*tuple[int], *Tuple[int]]
    #\ TYPE tuple[int, int]

    a: Union[((int, int,))]
    #\ TYPE int

    a: Union[(int, int,), int]
    #│       ^^^^^^^^^^^ ERROR Invalid type argument
    #\ TYPE int | Unknown

    a: Optional[int, int]
    #│          ^^^^^^^^ ERROR 'Optional' must have exactly one argument
    #\ TYPE Unknown FIXME Unknown | None

    a: Optional[(int,)]
    #\ TYPE int | None

    a: Callable[int, int]
    #│          ^^^ ERROR 'Callable' first parameter must be a parameter expression
    #\ TYPE int | None FIXME (Unknown) -> int

    a: Callable[[int], (([int]))]
    #│                   ^^^^^ ERROR Parameters to generic types must be types
    #\ TYPE (int) -> Unknown

    a: list[(([int]))]
    #│        ^^^^^ WARNING Passed type arguments do not match type parameters [_T] of class 'list'
    #\ TYPE list[[int]] FIXME list[Unknown]
    a: List[(([int]))]
    #│        ^^^^^ ERROR Parameters to generic types must be types
    #\ TYPE list[[int]] FIXME list[Unknown]

    a: C1[int]
    #\ TYPE C1[int]

    a: C1[int,]
    #\ TYPE C1[int]

    a: C1[((int))]
    #\ TYPE C1[int]

    a: C1[((int)),]
    #\ TYPE C1[int]

    a: C1[((int,))]
    #\ TYPE C1[int]

    a: C1[((((int)),))]
    #\ TYPE C1[int]

    a: C1[(((TV)))]
    #│       ^^ WARNING Unbound type variable
    #\ TYPE C1[TV] FIXME C1[Unknown]

    a: list[((TV))]
    #│        ^^ WARNING Unbound type variable
    #\ TYPE list[TV] FIXME list[Unknown]

    a: dict[((int)), (((TV)))]
    #│                  ^^ WARNING Unbound type variable
    #\ TYPE dict[int, TV] FIXME dict[int, Unknown]

    a: Annotated[((str, dict[str, str]))]
    """)

  @Test
  fun `subscription ellipsis type form`() = test("""
    from typing import List, Set, Dict, Tuple, Union, Optional

    class C1[T]: ...

    a: tuple[int, ...]
    #\ TYPE tuple[int, ...]
    a: Tuple[int, ...]
    #\ TYPE tuple[int, ...]

    a: tuple[..., int]
    #│       ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...]
    a: Tuple[..., int]
    #│       ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...]

    a: tuple[int, int, ...]
    #│                 ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...]
    a: Tuple[int, int, ...]
    #│                 ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...]

    a: tuple[int, ..., int]
    #│            ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...]
    a: Tuple[int, ..., int]
    #│            ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...]

    a: tuple[...]
    #│       ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...] FIXME tuple[Unknown]
    a: Tuple[...]
    #│       ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...] FIXME tuple[Unknown]

    a: tuple[..., ...]
    #│       ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...] FIXME tuple[Unknown]
    a: Tuple[..., ...]
    #│       ^^^ ERROR '...' is allowed only as the second of two arguments
    #\ TYPE tuple[int, ...] FIXME tuple[Unknown]

    a: list[...]
    #│      ^^^ ERROR Invalid type argument
    #\ TYPE list[Unknown]
    a: List[...]
    #│      ^^^ ERROR Invalid type argument
    #\ TYPE list[Unknown]

    a: set[...]
    #│     ^^^ ERROR Invalid type argument
    #\ TYPE set[Unknown]
    a: Set[...]
    #│     ^^^ ERROR Invalid type argument
    #\ TYPE set[Unknown]

    a: dict[...]
    #│      ^^^ ERROR Invalid type argument
    #\ TYPE dict[Unknown] FIXME dict[Unknown, Unknown]
    a: Dict[...]
    #│      ^^^ ERROR Invalid type argument
    #\ TYPE dict[Unknown] FIXME dict[Unknown, Unknown]

    a: Union[int, ...]
    #│            ^^^ ERROR Invalid type argument
    #\ TYPE int | Unknown

    a: Optional[...]
    #│          ^^^ ERROR Invalid type argument
    #\ TYPE Unknown FIXME Unknown | None

    a: tuple[*tuple[str], ...]
    #│                    ^^^ ERROR '...' cannot be used with an unpacked 'TypeVarTuple' or tuple
    #\ TYPE Unknown FIXME tuple[Unknown, ...]

    a: tuple[*tuple[str, ...], ...]
    #│                         ^^^ ERROR '...' cannot be used with an unpacked 'TypeVarTuple' or tuple
    #\ TYPE Unknown FIXME tuple[Unknown, ...]

    a: C1[...]
    #│    ^^^ ERROR Invalid type argument
    #\ TYPE C1[Unknown]
    """)

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  @TestFor(issues = ["PY-84289"])
  fun `exponential analysis time when map lookup key equals variable name`() = test(
    TestOptions(assertRecursionPrevention = false),
    """
    from a import config_response

    config_map = config_response["spec"]["config_map"]
    kafka_consumer_key1 = config_map["component.job.static.job2"]["spec.plugin.kafka.connectivity.in"]

    config_response = {}
    config_map = config_response["spec"]["config_map"]
    kafka_consumer_key2 = config_map["component.job.static.job2"]["spec.plugin.kafka.connectivity.in"]

    config_response = {}
    config_map = config_response["spec"]["config_map"]
    kafka_consumer_key3 = config_map["component.job.static.job2"]["spec.plugin.kafka.connectivity.in"]

    config_response = {}
    config_map = config_response["spec"]["config_map"]
    kafka_consumer_key4 = config_map["component.job.static.job2"]["spec.plugin.kafka.connectivity.in"]

    config_response = {}
    config_map = config_response["spec"]["config_map"]
    kafka_consumer_key5 = config_map["component.job.static.job2"]["spec.plugin.kafka.connectivity.in"]

    config_response = {}
    config_map = config_response["spec"]["config_map"]
    kafka_consumer_key6 = config_map["component.job.static.job2"]["spec.plugin.kafka.connectivity.in"]

    config_response = {}
    config_map = config_response["spec"]["config_map"]
    kafka_consumer_key7 = config_map["component.job.static.job2"]["spec.plugin.kafka.connectivity.in"]

    config_response = {}
    config_map = config_response["spec"]["config_map"]
    kafka_consumer_key8 = config_map["component.job.static.job2"]["spec.plugin.kafka.connectivity.in"]

    config_response = {}
    config_map = config_response["spec"]["config_map"]
    kafka_consumer_key9 = config_map["component.job.static.job2"]["spec.plugin.kafka.connectivity.in"]
    """,
    "a.py" to "config_response = {}",
  )

  @Test
  @TestFor(issues = ["PY-84570"])
  fun `list literal is not considered type alias`() = test("""
    from enum import Enum
    from typing import TypeAlias

    class Direction(Enum):
        NORTH = "N"
        SOUTH = "S"
        EAST = "E"
        WEST = "W"

    CARTESIAN = [Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST]
    print(CARTESIAN[0])

    type Alias = [int, str]
    #            ^^^^^^^^^^ WARNING Invalid type annotation
    myAlias: TypeAlias = [int, str]
    #                    ^^^^^^^^^^ WARNING Assigned value of type alias must be a correct type
    """)

  @Test
  @TestFor(issues = ["PY-85120"])
  fun `target expression with annotation not considered type alias`() = test("""
    a: list[int] | None = None  # Not a type alias

    if a:
        _ = a[1]  # No error expected
    """)

  @Test
  @TestFor(issues = ["PY-86310"])
  fun `target expression with reassignment not processed as implicit type alias`() = test("""
    b = []
    a = b
    _ = a[0] # No error expected
    """)

  @Test
  @TestFor(issues = ["PY-86223"])
  fun `generic type with quoted type parameter in type hint`() = test("""
    from typing import assert_type

    def foo[T](x: list["T"]):
        assert_type(x, list[T])
        assert_type(x, list["T"])
    """)

  @Test
  @TestFor(issues = ["PY-76895"])
  fun `invalid expression inside bound`() = test("""
    var = 1
    class ClassA[T: (3, bytes)]: ...
    #                └ WARNING Invalid type annotation
    class ClassB[T: (int, [1, 2, 3])]: ...
    #                     ^^^^^^^^^ WARNING Invalid type annotation
    class ClassC[T: (int, var)]: ...
    #                     ^^^ WARNING Invalid type annotation
    class ClassD[T: (int, lambda x: x)]: ...
    #                     ^^^^^^^^^^^ WARNING Invalid type annotation
    class ClassE[T: (int, ClassA[bytes]())]: ...
    #                     ^^^^^^^^^^^^^^^ WARNING Invalid type annotation

    class ClassF[T: (3, bytes)]: ...
    #                └ WARNING Invalid type annotation
    class ClassG[T: (int, [1, 2, 3])]: ...
    #                     ^^^^^^^^^ WARNING Invalid type annotation
    class ClassH[T: (int, var)]: ...
    #                     ^^^ WARNING Invalid type annotation
    class ClassI[T: (int, lambda x: x)]: ...
    #                     ^^^^^^^^^^^ WARNING Invalid type annotation
    class ClassJ[T: (int, ClassA[bytes]())]: ...
    #                     ^^^^^^^^^^^^^^^ WARNING Invalid type annotation
    class ClassK[T: [int]]: ...
    #               ^^^^^ WARNING Invalid type annotation
    """)

  @Test
  @TestFor(issues = ["PY-89092"])
  fun `ParamSpec in bound`() = test("""
    from collections.abc import Callable

    class A[**P]: ...
    class B[T: Callable[[], None] = Callable[[], None]]: ...
    class C[T: A[[]] = A[[]]]: ...
    """)

  @Test
  @TestFor(issues = ["PY-76895"])
  fun `invalid expression in default`() = test("""
    var = 1
    class ClassA[T: (3, bytes)]: ...
    #                └ WARNING Invalid type annotation
    class ClassB[T: (int, [1, 2, 3])]: ...
    #                     ^^^^^^^^^ WARNING Invalid type annotation
    class ClassC[T: (int, var)]: ...
    #                     ^^^ WARNING Invalid type annotation
    class ClassD[T: (int, lambda x: x)]: ...
    #                     ^^^^^^^^^^^ WARNING Invalid type annotation
    class ClassE[T: (int, ClassA[bytes]())]: ...
    #                     ^^^^^^^^^^^^^^^ WARNING Invalid type annotation
    class ClassF[T: 3]: ...
    #               └ WARNING Invalid type annotation
    """)

  @Test
  @TestFor(issues = ["PY-87564"])
  fun `type variable bound with module qualifier`() = test(
    """
    import mod

    class A[T: mod.MyClass]: ...
    """,
    "mod.py" to "class MyClass: pass",
  )

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec components swapped`() = test("""
    def mixed_up[**P](*args: P.kwargs, **kwargs: P.args) -> None:
    #                        │                   ^^^^^^ WARNING 'P.args' can only be used to annotate '*args' parameters
    #                        ^^^^^^^^ WARNING 'P.kwargs' can only be used to annotate '**kwargs' parameters
        pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component on regular param`() = test("""
    def misplaced[**P](x: P.args) -> None:
    #                     ^^^^^^ WARNING ParamSpec component can only be used to annotate '*args' or '**kwargs' parameters
        pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component same for both`() = test("""
    def bad[**P](*args: P.args, **kwargs: P.args) -> None:
    #                                     ^^^^^^ WARNING 'P.args' can only be used to annotate '*args' parameters
        pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec components kwargs with illegal annotation`() = test("""
    from typing import Any

    def bad[**P](*args: P.args, **kwargs: Any) -> None:
    #                   ^^^^^^ WARNING 'P.args' and 'P.kwargs' must both be present in the same function signature
        pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component not in scope`() = test("""
    from typing import ParamSpec

    P = ParamSpec("P")
    def out_of_scope(*args: P.args, **kwargs: P.kwargs) -> None:
    #                       │                 └ WARNING ParamSpec 'P' must be a type parameter of the enclosing callable or class
    #                       └ WARNING ParamSpec 'P' must be a type parameter of the enclosing callable or class
        pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component as variable annotation`() = test("""
    def foo[**P]() -> None:
        stored_args: P.args
    #                ^^^^^^ WARNING ParamSpec component can only be used to annotate '*args' or '**kwargs' parameters
        stored_kwargs: P.kwargs
    #                  ^^^^^^^^ WARNING ParamSpec component can only be used to annotate '*args' or '**kwargs' parameters
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component unpaired args`() = test("""
    def just_args[**P](*args: P.args) -> None:
    #                         ^^^^^^ WARNING 'P.args' and 'P.kwargs' must both be present in the same function signature
        pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component unpaired kwargs`() = test("""
    def just_kwargs[**P](**kwargs: P.kwargs) -> None:
    #                              ^^^^^^^^ WARNING 'P.args' and 'P.kwargs' must both be present in the same function signature
        pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component keyword-only between`() = test("""
    def bar[**P](*args: P.args, s: str, **kwargs: P.kwargs) -> None:
    #                           ^^^^^^ WARNING No parameters allowed between 'P.args' and 'P.kwargs'
        pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component in scope via Generic class`() = test("""
    from typing import ParamSpec, Generic

    P = ParamSpec("P")
    class Wrapper(Generic[P]):
        def call(self, *args: P.args, **kwargs: P.kwargs) -> None:
            pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component in scope via Protocol class`() = test("""
    from typing import ParamSpec, Protocol

    P = ParamSpec("P")
    class Proto(Protocol[P]):
        def __call__(self, *args: P.args, **kwargs: P.kwargs) -> None: ...
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component in scope new-style generic class`() = test("""
    class Wrapper[**P]:
        def call(self, *args: P.args, **kwargs: P.kwargs) -> None:
            pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec component not in scope in class`() = test("""
    from typing import ParamSpec

    P = ParamSpec("P")
    class NoParamSpec:
        def call(self, *args: P.args, **kwargs: P.kwargs) -> None:
    #                         │                 └ WARNING ParamSpec 'P' must be a type parameter of the enclosing callable or class
    #                         └ WARNING ParamSpec 'P' must be a type parameter of the enclosing callable or class
            pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `ParamSpec components valid usage`() = test("""
    from typing import Callable, ParamSpec

    P = ParamSpec("P")

    def valid1[**P](*args: P.args, **kwargs: P.kwargs) -> None:
        pass

    def valid2[**P](s: str, *args: P.args, **kwargs: P.kwargs) -> None:
        pass

    def twice(f: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> int:
        return f(*args, **kwargs)
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `after ParamSpec args kwargs param without annotation`() = test("""
    from typing import ParamSpec, TypeVar, Callable

    P = ParamSpec("P")
    T = TypeVar("T")

    def invoke(fn: Callable[P, T], *args: P.args, **kwargs) -> T:
    #                                     ^^^^^^ WARNING 'P.args' and 'P.kwargs' must both be present in the same function signature
        pass
    """)

  @Test
  @TestFor(issues = ["PY-76850"])
  fun `illegal ParamSpec usage for kwargs`() = test("""
    from typing import ParamSpec, TypeVar, Callable

    P = ParamSpec("P")
    def invoke(**kwargs: P.kwargs) -> None:
    #                    ^^^^^^^^ WARNING 'P.args' and 'P.kwargs' must both be present in the same function signature
    #                    └ WARNING ParamSpec 'P' must be a type parameter of the enclosing callable or class
        pass
    """)

  @Test
  fun `explicit tuple in Literal`() = test("""
    from typing import Literal

    _: Literal[(1, "a")]
    #          ^^^^^^^^ WARNING 'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types
    """)

  companion object {
    private const val TRIPLE_QUOTE = "\"\"\""
  }
}
