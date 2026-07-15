// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyInspectionTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.intellij.lang.annotations.Language

@Subsystems.Inspections
@Layers.Functional
class PyTypeHintsInspectionTest : PyInspectionTestCase() {

  @TestFor(issues = ["PY-28243"])
  fun `test TypeVar and target name`() {
    doTest("""
                   from typing import TypeVar

                   T0 = TypeVar('T0')
                   T1 = TypeVar(<warning descr="The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned">'T2'</warning>)""")
  }

  @TestFor(issues = ["PY-28243"])
  fun `test TypeVar placement`() {
    doTest("""
                   from typing import List, TypeVar

                   T0 = TypeVar('T0')
                   a: List[<warning descr="Unbound type variable">T0</warning>]
                   b: List[<warning descr="A 'TypeVar()' expression must always directly be assigned to a variable">TypeVar('T1')</warning>]""")
  }

  @TestFor(issues = ["PY-28243"])
  fun `test TypeVar redefinition`() {
    doTest("""
                   from typing import TypeVar

                   T0 = TypeVar('T0')
                   print(T0)
                   <warning descr="Type variables must not be redefined">T0</warning> = TypeVar('T0')""")
  }

  @TestFor(issues = ["PY-28124"])
  fun `test TypeVar bivariant`() {
    doTest("""
                   from typing import TypeVar

                   T1 = <error descr="Bivariant type variables are not supported">TypeVar('T1', contravariant=True, covariant=True)</error>
                   true = True
                   T2 = <error descr="Bivariant type variables are not supported">TypeVar('T2', contravariant=true, covariant=true)</error>""")
  }

  @TestFor(issues = ["PY-28124"])
  fun `test TypeVar constraints and bound`() {
    doTest("""
                   from typing import TypeVar

                   T2 = <error descr="Constraints cannot be combined with bound=...">TypeVar('T2', int, str, bound=str)</error>""")
  }

  @TestFor(issues = ["PY-28124"])
  fun `test TypeVar - number of constraints`() {
    doTest("""
                   from typing import TypeVar

                   T1 = <error descr="A single constraint is not allowed">TypeVar('T1', int)</error>
                   T2 = TypeVar('T2', int, str)""")
  }

  @TestFor(issues = ["PY-28124"])
  fun `test TypeVar name as Literal`() {
    doTest("""
                   from typing import TypeVar

                   name = 'T0'
                   T0 = TypeVar(<warning descr="'TypeVar()' expects a string literal as first argument">name</warning>)
                   T1 = TypeVar('T1')""")
  }

  @TestFor(issues = ["PY-28243"])
  fun `test TypeVar parameterized constraints`() {
    doTest("""
                   from typing import TypeVar, List

                   T1 = TypeVar('T1', int, str)

                   T2 = TypeVar('T2', int, <warning descr="Constraints cannot be parametrized by type variables">List[<warning descr="Unbound type variable">T1</warning>]</warning>)
                   T3 = TypeVar('T3', bound=<warning descr="Constraints cannot be parametrized by type variables">List[<warning descr="Unbound type variable">T1</warning>]</warning>)

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
                   T55 = TypeVar('T55', bound=my_list_int)""")
  }

  @TestFor(issues = ["PY-28227"])
  fun `test plain Generic inheritance`() {
    doTest("""
                   from typing import Generic

                   class A(<error descr="Cannot inherit from plain 'Generic'">Generic</error>):
                       pass

                   B = Generic
                   class C(<error descr="Cannot inherit from plain 'Generic'">B</error>):
                       pass""")
  }

  @TestFor(issues = ["PY-28227"])
  fun `test Generic parameters types`() {
    doTest("""
                   from typing import Generic, Protocol, TypeVar

                   class A1(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">0</error>]):
                       pass

                   class B1(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">int</error>]):
                       pass
                   
                   class B11(Protocol[<error descr="Parameters to 'Protocol[...]' must all be type variables">int</error>]):
                       pass

                   class A2(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">0</error>, <error descr="Parameters to 'Generic[...]' must all be type variables">0</error>]):
                       pass

                   class B2(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">int</error>, <error descr="Parameters to 'Generic[...]' must all be type variables">int</error>]):
                       pass

                   null = 0
                   class A3(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">null</error>]):
                       pass

                   my_int = int
                   class B3(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">my_int</error>]):
                       pass

                   T = TypeVar('T')
                   S = TypeVar('S')

                   class C1(Generic[T]):
                       pass

                   class C2(Generic[T, S]):
                       pass

                   my_t = T
                   class C3(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">my_t</error>]):
                       pass

                   class D1:
                       pass

                   class D2:
                       pass

                   class E1(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">D1</error>]):
                       pass

                   class F1(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">D1</error>, <error descr="Parameters to 'Generic[...]' must all be type variables">D2</error>]):
                       pass

                   my_d = D1
                   class E2(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">my_d</error>]):
                       pass""")
  }

  @TestFor(issues = ["PY-28227"])
  fun `test Generic parameters duplication`() {
    doTest("""
                   from typing import Generic, TypeVar

                   T = TypeVar('T')

                   class C(Generic[T, <error descr="Parameters to 'Generic[...]' must all be unique">T</error>]):
                       pass

                   B = Generic
                   class A(B[T, <error descr="Parameters to 'Generic[...]' must all be unique">T</error>]):
                       pass

                   T1 = T
                   class D(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">T1</error>, T]):
                       pass""")
  }

  @TestFor(issues = ["PY-28227"])
  fun `test Generic duplication`() {
    doTest("""
                   from typing import Generic, TypeVar

                   T = TypeVar('T')
                   S = TypeVar('S')

                   class C(Generic[T], <error descr="Cannot inherit from 'Generic[...]' multiple times">Generic[S]</error>):
                       pass

                   B = Generic
                   class D(B[T], <error descr="Cannot inherit from 'Generic[...]' multiple times">Generic[S]</error>):
                       pass

                   E = Generic[T]
                   class A(E, <error descr="Cannot inherit from 'Generic[...]' multiple times">Generic[S]</error>):
                       pass""")
  }

  @TestFor(issues = ["PY-28227"])
  fun `test Generic completeness`() {
    doTest("""
                   from typing import Generic, TypeVar, Iterable, Protocol

                   T = TypeVar('T')
                   S = TypeVar('S')

                   class C<error descr="'Generic[...]' or 'Protocol[...]' should list all type variables (S)">(Generic[T], Iterable[S])</error>:
                       pass
                   
                   class P<error descr="'Generic[...]' or 'Protocol[...]' should list all type variables (S)">(Iterable[S], Protocol[T])</error>:
                       pass

                   B = Generic
                   D = T
                   class A<error descr="'Generic[...]' or 'Protocol[...]' should list all type variables (S)">(B[<error descr="Parameters to 'Generic[...]' must all be type variables">D</error>], Iterable[S])</error>:
                       pass

                   class E(Generic[T], Iterable[T]):
                       pass

                   class F(B[<error descr="Parameters to 'Generic[...]' must all be type variables">D</error>]):
                       pass
                   
                   class G(Iterable[T]):
                       pass
                   """)
  }

  @TestFor(issues = ["PY-31147"])
  fun `test Generic completeness partially specialized`() {
    doTest("""
                   from typing import TypeVar, Generic, Dict

                   T = TypeVar("T")

                   class C(Generic[T], Dict[int, T]):
                       pass""")
  }

  @TestFor(issues = ["PY-78767"])
  fun `test Generic metaclasses are not supported`() {
    doTest("""
                   from typing import Any, Generic, TypeVar
                   
                   T = TypeVar("T")
                   
                   class MyMetaClass(type, Generic[T]): ...
                   
                   class MyClass1(Generic[T], metaclass=<warning descr="Metaclass cannot be generic">MyMetaClass[T]</warning>): ...
                   class MyClass2(metaclass=MyMetaClass[Any]): ...""")
  }

  @TestFor(issues = ["PY-76866"])
  fun `test unbound type parameter`() {
    doTest(
      """
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
        
        v1: <warning descr="Unbound type variable">T</warning>
        v2: list[<warning descr="Unbound type variable">T</warning>]
        
        list[<warning descr="Unbound type variable">T</warning>]()
        
        def f1(x: T) -> None:
            a1: T
            a2: list[T] = []
            a3: <warning descr="Unbound type variable">S</warning>
            a4: list[<warning descr="Unbound type variable">S</warning>] = []
        
            list[T]()
            list[<warning descr="Unbound type variable">S</warning>]()
        
        def f2() -> T:
            x: T
            raise Exception()
        
        class Bar(Generic[T]):
            attr1: T
            attr2: list[T] = []
            attr3: <warning descr="Unbound type variable">S</warning>
            attr4: list[<warning descr="Unbound type variable">S</warning>] = []
        
            def do_something(self, x: S) -> S:
                ...
            def do_something_else(self, other: 'Bar[T]'):
                ...""")
  }

  @TestFor(issues = ["PY-78878"])
  fun `test Generic class can not use type variables from outer scope`() {
    doTest("""
                   from typing import TypeVar, Generic, Iterable
                   
                   T = TypeVar('T')
                   S = TypeVar('S')
                   
                   def a_fun(x: T) -> None:
                       a_list: list[T] = []
                   
                       class <warning descr="Some type variables (T) are already in use by an outer scope">MyGeneric</warning>(Generic[T]):
                           ...
                   
                   def a_fun_new_syntax1[U]() -> None:
                       class <warning descr="Some type variables (U) are already in use by an outer scope">MyGeneric</warning>(Generic[U]):
                           ...
                   
                   def a_fun_new_syntax2[U](u: U) -> None:
                       class <warning descr="Some type variables (U) are already in use by an outer scope">MyGeneric</warning>(Generic[U]):
                           ...
                   
                   class Outer(Generic[T]):
                       class <warning descr="Some type variables (T) are already in use by an outer scope">Bad</warning>(Iterable[T]):
                           ...
                       class AlsoBad:
                           x: list[<warning descr="Unbound type variable">T</warning>]
                   
                       class Inner(Iterable[S]):
                           ...
                       attr: Inner[T]
                   
                   class OuterNewSyntax[U]:
                       class <warning descr="Some type variables (U) are already in use by an outer scope">Bad</warning>(Generic[U]):
                           ...""")
  }

  @TestFor(issues = ["PY-82835"])
  fun `test type parameter is already in use by outer scope`() {
    doTest("""
                   from typing import Sequence, TypeAlias
                   
                   T = 0
                   
                   
                   class ClassA[T](Sequence[T]):
                       T = 1
                   
                       def method1[<warning descr="Type parameter 'T' is already in use by an outer scope">T</warning>](self):
                           ...
                   
                       def method2[<warning descr="Type parameter 'T' is already in use by an outer scope">T</warning>](self, x=T):
                           ...
                   
                       def method3[<warning descr="Type parameter 'T' is already in use by an outer scope">T</warning>](self, x: T):
                           ...
                   
                       class Inner[<warning descr="Type parameter 'T' is already in use by an outer scope">T</warning>]:
                           ...
                   """)
  }

  fun `test self annotation uses class-scoped type parameters`() {
    doTest("""
                   class MyClass[T1, T2]:
                       def __init__(self: <warning descr="Class-scoped type variables should not be used in the annotation for 'self' parameter of '__init__' method">MyClass[T2, T1]</warning>) -> None: ...
                   """)
  }

  fun `test inconsistent TypeVar order`() {
    doTest("""
                   from typing import Generic, TypeVar
               
                   T1 = TypeVar('T1')
                   T2 = TypeVar('T2')
               
                   class Grandparent(Generic[T1, T2]): ...
                   class Parent(Grandparent[T1, T2]): ...
                   class BadChild(Parent[T1, T2], <warning descr="Generic base class 'Grandparent' is inherited with inconsistent type arguments: 'Grandparent[T1, T2]' and 'Grandparent[T2, T1]'">Grandparent[T2, T1]</warning>): ...
                   """)
  }

  fun `test inconsistent TypeVar order diamond`() {
    doTest("""
                   from typing import Generic, TypeVar
               
                   T1 = TypeVar('T1')
                   T2 = TypeVar('T2')
               
                   class Base(Generic[T1, T2]): ...
                   class Left(Base[T1, T2]): ...
                   class Right(Base[T2, T1]): ...
                   class BadDiamond(Left[T1, T2], <warning descr="Generic base class 'Base' is inherited with inconsistent type arguments: 'Base[T1, T2]' and 'Base[T2, T1]'">Right[T1, T2]</warning>): ...
                   """)
  }

  fun `test consistent TypeVar order with reordered intermediate`() {
    doTest("""
                   from typing import Generic, TypeVar
               
                   T1 = TypeVar('T1')
                   T2 = TypeVar('T2')
               
                   class Base(Generic[T1, T2]): ...
                   class Reordered(Generic[T1, T2], Base[T2, T1]): ...
                   class GoodChild(Reordered[T1, T2], Base[T2, T1]): ...
                   """)
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Any`() {
    doTest("""
                   from typing import Any

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Any' cannot be used with instance and class checks">Any</error>)
                   B = Any
                   assert issubclass(A, <error descr="'Any' cannot be used with instance and class checks">B</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on NoReturn`() {
    doTest("""
                   from typing import NoReturn

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'NoReturn' cannot be used with instance and class checks">NoReturn</error>)
                   B = NoReturn
                   assert issubclass(A, <error descr="'NoReturn' cannot be used with instance and class checks">B</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on TypeVar`() {
    doTest("""
                   from typing import TypeVar

                   T = TypeVar("T")

                   class A:
                       pass

                   assert isinstance(A(), TypeVar)
                   assert issubclass(A, TypeVar)

                   assert isinstance(A(), <error descr="Type variables cannot be used with instance and class checks">T</error>)
                   assert issubclass(A, <error descr="Type variables cannot be used with instance and class checks">T</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Union before 310`() {
    runWithLanguageLevel(LanguageLevel.PYTHON39) {
      doTest("""
                     from typing import Union

                     class A:
                         pass

                     assert isinstance(A(), <error descr="'Union' cannot be used with instance and class checks">Union</error>)
                     B = Union
                     assert issubclass(A, <error descr="'Union' cannot be used with instance and class checks">B</error>)

                     assert isinstance(A(), <error descr="'Union' cannot be used with instance and class checks">Union[int, str]</error>)
                     assert issubclass(A, <error descr="'Union' cannot be used with instance and class checks">B[int, str]</error>)
                     C = B[int, str]
                     assert issubclass(A, <error descr="'Union' cannot be used with instance and class checks">C</error>)
                     assert isinstance(A(), <error descr="'Union' cannot be used with instance and class checks">Union[str, Union[str, Union[list, dict]]]</error>)
                     assert isinstance(A(), <error descr="'Union' cannot be used with instance and class checks">Union[str, Union[str, Union[list[int], dict]]]</error>)
                     assert isinstance(A(), <error descr="Python version 3.9 does not allow writing union types as X | Y">int | str</error>)
                     assert isinstance(A(), <error descr="Python version 3.9 does not allow writing union types as X | Y">int | list[str]</error>)
                     assert issubclass(A, <error descr="Python version 3.9 does not allow writing union types as X | Y">int | str</error>)
                     assert issubclass(A, <error descr="Python version 3.9 does not allow writing union types as X | Y">int | list[str]</error>)""")
    }
  }

  @TestFor(issues = ["PY-44974"])
  fun `test instance and class checks on Union from future annotations`() {
    runWithLanguageLevel(LanguageLevel.PYTHON39) {
      doTest("""
                     from typing import Union
                     from __future__ import annotations

                     class A:
                         pass

                     assert isinstance(A(), <error descr="'Union' cannot be used with instance and class checks">Union</error>)
                     B = Union
                     assert issubclass(A, <error descr="'Union' cannot be used with instance and class checks">B</error>)

                     assert isinstance(A(), <error descr="'Union' cannot be used with instance and class checks">Union[int, str]</error>)
                     assert issubclass(A, <error descr="'Union' cannot be used with instance and class checks">B[int, str]</error>)
                     C = B[int, str]
                     assert issubclass(A, <error descr="'Union' cannot be used with instance and class checks">C</error>)
                     assert isinstance(A(), <error descr="'Union' cannot be used with instance and class checks">Union[str, Union[str, Union[list, dict]]]</error>)
                     assert isinstance(A(), <error descr="'Union' cannot be used with instance and class checks">Union[str, Union[str, Union[list[int], dict]]]</error>)
                     assert isinstance(A(), <error descr="Python version 3.9 does not allow writing union types as X | Y">int | str</error>)
                     assert isinstance(A(), <error descr="Python version 3.9 does not allow writing union types as X | Y">int | list[str]</error>)
                     assert issubclass(A, <error descr="Python version 3.9 does not allow writing union types as X | Y">int | str</error>)
                     assert issubclass(A, <error descr="Python version 3.9 does not allow writing union types as X | Y">int | list[str]</error>)""")
    }
  }

  @TestFor(issues = ["PY-44974"])
  fun `test instance and class checks on Union`() {
    doTest("""
                   from typing import Union

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Union' cannot be used with instance and class checks">Union</error>)
                   B = Union
                   assert issubclass(A, <error descr="'Union' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), Union[int, str])
                   assert issubclass(A, B[int, str])
                   C = B[int, str]
                   assert issubclass(A, C)
                   assert isinstance(A(), Union[str, Union[str, Union[list, dict]]])
                   assert isinstance(A(), Union[str, Union[str, Union[<error descr="Parameterized generics cannot be used with instance and class checks">list[int]</error>, dict]]])
                   assert isinstance(A(), int | str)
                   assert isinstance(A(), int | <error descr="Parameterized generics cannot be used with instance and class checks">list[str]</error>)
                   assert issubclass(A, int | str)
                   assert issubclass(A, int | <error descr="Parameterized generics cannot be used with instance and class checks">list[str]</error>)
                   """)
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Optional before 310`() {
    runWithLanguageLevel(LanguageLevel.PYTHON39) {
      doTest("""
                     from typing import Optional

                     class A:
                         pass

                     assert isinstance(A(), <error descr="'Optional' cannot be used with instance and class checks">Optional</error>)
                     B = Optional
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">B</error>)

                     assert isinstance(A(), <error descr="'Optional' cannot be used with instance and class checks">Optional[int]</error>)
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">B[int]</error>)
                     C = B[int]
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">C</error>)""")
    }
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Optional from future annotations`() {
    runWithLanguageLevel(LanguageLevel.PYTHON39) {
      doTest("""
                     from typing import Optional
                     from __future__ import annotations

                     class A:
                         pass

                     assert isinstance(A(), <error descr="'Optional' cannot be used with instance and class checks">Optional</error>)
                     B = Optional
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">B</error>)

                     assert isinstance(A(), <error descr="'Optional' cannot be used with instance and class checks">Optional[int]</error>)
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">B[int]</error>)
                     C = B[int]
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">C</error>)""")
    }
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Optional`() {
    doTest("""
                   from typing import Optional

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Optional' cannot be used with instance and class checks">Optional</error>)
                   B = Optional
                   assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), Optional[int])
                   assert issubclass(A, B[int])
                   C = B[int]
                   assert issubclass(A, C)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on ClassVar`() {
    doTest("""
                   from typing import ClassVar

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'ClassVar' cannot be used with instance and class checks">ClassVar</error>)
                   B = ClassVar
                   assert issubclass(A, <error descr="'ClassVar' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), <error descr="'ClassVar' cannot be used with instance and class checks">ClassVar[int]</error>)
                   assert issubclass(A, <error descr="'ClassVar' cannot be used with instance and class checks">B[int]</error>)
                   C = B[int]
                   assert issubclass(A, <error descr="'ClassVar' cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Generic`() {
    doTest("""
                   from typing import TypeVar, Generic

                   T = TypeVar("T")

                   class A:
                       pass

                   assert isinstance(A(), Generic)
                   B = Generic
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="'Generic' cannot be used with instance and class checks">Generic[T]</error>)
                   assert issubclass(A, <error descr="'Generic' cannot be used with instance and class checks">B[T]</error>)
                   C = B[T]
                   assert issubclass(A, <error descr="'Generic' cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-34945"])
  fun `test instance and class checks on Final`() {
    doTest("""
                   from typing import TypeVar
                   from typing_extensions import Final

                   T = TypeVar("T")

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Final' cannot be used with instance and class checks">Final</error>)
                   B = Final
                   assert issubclass(A, <error descr="'Final' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), <error descr="'Final' cannot be used with instance and class checks">Final[T]</error>)
                   assert issubclass(A, <error descr="'Final' cannot be used with instance and class checks">B[T]</error>)
                   C = B[T]
                   assert issubclass(A, <error descr="'Final' cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-35235"])
  fun `test instance and class checks on Literal`() {
    doTest("""
                   from typing_extensions import Literal

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Literal' cannot be used with instance and class checks">Literal</error>)
                   B = Literal
                   assert issubclass(A, <error descr="'Literal' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), <error descr="'Literal' cannot be used with instance and class checks">Literal[1]</error>)
                   assert issubclass(A, <error descr="'Literal' cannot be used with instance and class checks">B[1]</error>)
                   C = B[1]
                   assert issubclass(A, <error descr="'Literal' cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-42334"])
  fun `test instance and class checks on TypeAlias`() {
    doTest("""
                   from typing import TypeAlias

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'TypeAlias' cannot be used with instance and class checks">TypeAlias</error>)
                   assert issubclass(A, <error descr="'TypeAlias' cannot be used with instance and class checks">TypeAlias</error>)
                   B = TypeAlias
                   assert isinstance(A(), <error descr="'TypeAlias' cannot be used with instance and class checks">B</error>)
                   assert issubclass(A, <error descr="'TypeAlias' cannot be used with instance and class checks">B</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on generic inheritor`() {
    doTest("""
                   from typing import TypeVar, List

                   T = TypeVar("T")

                   class A:
                       pass

                   assert isinstance(A(), List)
                   B = List
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">List[T]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[T]</error>)
                   C = B[T]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Tuple`() {
    doTest("""
                   from typing import Tuple

                   class A:
                       pass

                   assert isinstance(A(), Tuple)
                   B = Tuple
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">Tuple[int, str]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[int, str]</error>)
                   C = B[int, str]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Type`() {
    doTest("""
                   from typing import Type

                   class A:
                       pass

                   assert isinstance(A(), Type)
                   B = Type
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">Type[int]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[int]</error>)
                   C = B[int]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Callable`() {
    doTest("""
                   from typing import Callable

                   class A:
                       pass

                   assert isinstance(A(), Callable)
                   B = Callable
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">Callable[..., str]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[..., str]</error>)
                   C = B[..., str]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on Protocol`() {
    doTest("""
                   from typing import Protocol, TypeVar

                   class A:
                       pass

                   T = TypeVar("T")

                   assert isinstance(A(), Protocol)
                   B = Protocol
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">Protocol[T]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[T]</error>)
                   C = B[T]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on user class`() {
    doTest("""
                   from typing import Generic, TypeVar

                   class A:
                       pass

                   T = TypeVar("T")

                   class D(Generic[T]):
                       pass

                   assert isinstance(A(), D)
                   B = D
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">D[int]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[int]</error>)
                   C = B[int]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-28249"])
  fun `test instance and class checks on unknown`() {
    doTest("""
                   from m1 import D

                   class A:
                       pass

                   assert isinstance(A(), D)
                   B = D
                   assert issubclass(A, B)

                   assert isinstance(A(), D[int])
                   assert issubclass(A, B[int])
                   C = B[int]
                   assert issubclass(A, C)""")
  }

  @TestFor(issues = ["PY-31788"])
  fun `test instance and class checks on generic parameter`() {
    doTest("""
                   from typing import List, Type, TypeVar

                   T = TypeVar("T")

                   class A:
                       pass

                   def foo(p1: T, p2: Type[T], p3: List[T]):
                       assert isinstance(A(), <error descr="Type variables cannot be used with instance and class checks">p1</error>)
                       assert issubclass(A, <error descr="Type variables cannot be used with instance and class checks">p1</error>)

                       assert isinstance(A(), p2)
                       assert issubclass(A, p2)

                       assert isinstance(A(), <error descr="Type variables cannot be used with instance and class checks">p3</error>)
                       assert issubclass(A, <error descr="Type variables cannot be used with instance and class checks">p3</error>)""")
  }

  fun `test TypedDict with instance and class checks`() {
    doTest(
      """
        from typing import TypedDict

        class Movie(TypedDict):
            name: str
            year: int
        
        Movie2 = TypedDict('Movie2', {'name': str, 'year': int})

        class A:
            pass

        def foo(d):
          if isinstance(d, <error descr="TypedDict type cannot be used with instance and class checks">Movie</error>):
              pass

          if isinstance(d, <error descr="TypedDict type cannot be used with instance and class checks">Movie2</error>):
              pass

        M = Movie
        if issubclass(A, <error descr="TypedDict type cannot be used with instance and class checks">M</error>):
            pass

        M2 = Movie2
        if issubclass(A, <error descr="TypedDict type cannot be used with instance and class checks">M2</error>):
            pass
        """
    )
  }

  fun `test TypedDict as TypeVar bound`() {
    doTest(
      """
        from typing import TypedDict, TypeVar
        
        class Movie(TypedDict):
            name: str
            year: int

        T = TypeVar("T", bound=<warning descr="TypedDict is not allowed as a bound for a TypeVar">TypedDict</warning>)
        U = TypeVar("U", bound=Movie)
        """
    )
  }

  @TestFor(issues = ["PY-16853"])
  fun `test parentheses and typing`() {
    doTest("""
                   from typing import Union, TypeAlias

                   def a(b: <error descr="Generics should be specified through square brackets">Union(int, str)</error>):
                       pass

                   def c(d):
                       # type: (<error descr="Generics should be specified through square brackets">Union(int, str)</error>) -> None
                       pass

                   def e(f: <error descr="Generics should be specified through square brackets">Union()</error>):
                       pass

                   def g(h):
                       # type: (<error descr="Generics should be specified through square brackets">Union()</error>) -> None
                       pass

                   v1 = Union(int, str)
                   v2 = None  # type: <error descr="Generics should be specified through square brackets">Union(int, str)</error>
                   
                   U = Union
                   def i(j: <error descr="Generics should be specified through square brackets">U(int, str)</error>):
                       pass

                   v3 = U(int, str)

                   with foo() as bar:  # type: <error descr="Generics should be specified through square brackets">Union(int,str)</error>
                       pass

                   for x in []:  # type: <error descr="Generics should be specified through square brackets">Union(int,str)</error>
                       pass

                   A1: TypeAlias = <error descr="Generics should be specified through square brackets">Union(int, str)</error>
                   A2: TypeAlias = <warning descr="Assigned value of type alias must be a correct type">'<error descr="Generics should be specified through square brackets">Union(int, str)</error>'</warning>
                   A3 = <error descr="Generics should be specified through square brackets">Union(int, str)</error>  # type: TypeAlias
                   A3 = <warning descr="Assigned value of type alias must be a correct type">'<error descr="Generics should be specified through square brackets">Union(int, str)</error>'</warning>  # type: TypeAlias""")
  }

  @TestFor(issues = ["PY-57155"])
  fun `test parentheses in Annotated`() {
    doTest("""
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
                   
                   def e(x: Annotated[str, list[<error descr="Invalid type argument">dict(key="value")</error>]]):
                      pass
                   
                   def f(x: Annotated[<warning descr="Generics should be specified through square brackets">dict(key="value")</warning>, ""]):
                      pass""")
  }

  @TestFor(issues = ["PY-32634"])
  fun `test parentheses in assignment`() {
    doTest("""
                  from typing import DefaultDict, TypeAlias
                  
                  example = DefaultDict(int)
                  
                  ExampleAlias: TypeAlias = <error descr="Generics should be specified through square brackets">DefaultDict(int)</error>
                  
                  type ExampleType = <error descr="Generics should be specified through square brackets">DefaultDict(int)</error>
                  """)
  }

  @TestFor(issues = ["PY-16853"])
  fun `test parentheses and custom`() {
    doTest("""
                   from typing import Generic, TypeVar, TypeAlias

                   T = TypeVar("T")

                   class A(Generic[T]):
                       def __init__(self, v):
                           pass

                   def a(b: <warning descr="Generics should be specified through square brackets"><warning descr="Invalid type annotation">A(int)</warning></warning>):
                       pass

                   def c(d):
                       # type: (<warning descr="Generics should be specified through square brackets">A(int)</warning>) -> None
                       pass

                   def e(f: <warning descr="Generics should be specified through square brackets"><warning descr="Invalid type annotation">A()</warning></warning>):
                       pass

                   def g(h):
                       # type: (<warning descr="Generics should be specified through square brackets">A()</warning>) -> None
                       pass

                   v1 = A(int)
                   v2 = None  # type: <warning descr="Generics should be specified through square brackets">A(int)</warning>

                   U = A
                   def i(j: <warning descr="Generics should be specified through square brackets"><warning descr="Invalid type annotation">U(int)</warning></warning>):
                       pass

                   v3 = None  # type: <warning descr="Generics should be specified through square brackets">U(int)</warning>

                   A1: TypeAlias = <warning descr="Assigned value of type alias must be a correct type"><warning descr="Generics should be specified through square brackets">A(int)</warning></warning>
                   A2: TypeAlias = <warning descr="Assigned value of type alias must be a correct type">'<warning descr="Generics should be specified through square brackets">A(int)</warning>'</warning>
                   A3 = <warning descr="Assigned value of type alias must be a correct type"><warning descr="Generics should be specified through square brackets">A(int)</warning></warning>  # type: TypeAlias
                   A4 = <warning descr="Assigned value of type alias must be a correct type">'<warning descr="Generics should be specified through square brackets">A(int)</warning>'</warning>  # type: TypeAlias""")
  }

  @TestFor(issues = ["PY-20530"])
  fun `test Callable parameters`() {
    doTest("""
                   from typing import Callable, TypeAlias

                   a: Callable[..., str]
                   b: Callable[[int], str]
                   c: Callable[[int, str], str]

                   d: Callable[<error descr="'Callable' must be used as 'Callable[[arg, ...], result]'">...</error>]
                   e: Callable[<error descr="'Callable' first parameter must be a parameter expression">int</error>, str]
                   f: Callable[<error descr="'Callable' must be used as 'Callable[[arg, ...], result]'">int, str</error>, str]
                   g: Callable[<error descr="'Callable' first parameter must be a parameter expression">(int, str)</error>, str]
                   h: Callable[<error descr="'Callable' must be used as 'Callable[[arg, ...], result]'">int</error>]
                   h: Callable[<error descr="'Callable' first parameter must be a parameter expression">(int)</error>, str]

                   A1: TypeAlias = Callable[<error descr="'Callable' must be used as 'Callable[[arg, ...], result]'">int</error>]
                   A2: TypeAlias = 'Callable[<error descr="'Callable' must be used as 'Callable[[arg, ...], result]'">int</error>]'
                   A3 = Callable[<error descr="'Callable' must be used as 'Callable[[arg, ...], result]'">int</error>]  # type: TypeAlias
                   A4 = 'Callable[<error descr="'Callable' must be used as 'Callable[[arg, ...], result]'">int</error>]'  # type: TypeAlias""")
  }

  @TestFor(issues = ["PY-20530"])
  fun self() {
    doTest("""
                   class A:
                       def method(self, i: int):
                           v1: <error descr="Invalid type 'self'">self</error>.B
                           v2 = None  # type: <error descr="Invalid type 'self'">self</error>.B
                           print(self.B)

                       class B:
                           pass

                   class self:
                       pass

                   v: self""")
  }

  @TestFor(issues = ["PY-20530"])
  fun `test tuple unpacking`() {
    doTest("""
                   a1 = undefined()  # type: int

                   b1, (c1, d1) = undefined()  # type: int, (int, str)
                   e1, (f1, g1), h1 = undefined()  # type: int, (str, int), str

                   b2, (c2, d2) = undefined()  # type: <warning descr="Type comment cannot be matched with unpacked variables">int, (int)</warning>
                   e2, (f2, g2), h2 = undefined()  # type: <warning descr="Type comment cannot be matched with unpacked variables">int, (str), str</warning>""")
  }

  @TestFor(issues = ["PY-20530"])
  fun `test annotation and type comment`() {
    doTest(
      """
        a<warning descr="Types specified both in a type comment and annotation">: int</warning> = None  <warning descr="Types specified both in a type comment and annotation"># type: int</warning>

        def foo(a<warning descr="Types specified both in a type comment and annotation">: int</warning>  <warning descr="Types specified both in a type comment and annotation"># type: int</warning>
                ,):
            pass

        def <warning descr="Types specified both in a type comment and annotation">bar</warning>(a: int) -> int:
            <warning descr="Types specified both in a type comment and annotation"># type: (int) -> int</warning>
            pass

        def <warning descr="Types specified both in a type comment and annotation">baz1</warning>(a: int):
            <warning descr="Types specified both in a type comment and annotation"># type: (int) -> int</warning>
            pass

        def <warning descr="Types specified both in a type comment and annotation">baz2</warning>(a) -> int:
            <warning descr="Types specified both in a type comment and annotation"># type: (int) -> int</warning>
            pass"""
    )
  }

  @TestFor(issues = ["PY-20530"])
  fun `test valid type comment and parameters`() {
    doTest("""
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
                           pass""")
  }

  @TestFor(issues = ["PY-20530"])
  fun `test invalid type comment and parameters`() {
    doTest("""
                   from typing import Type

                   class Bar:
                       # self is specified
                       def spam1(self):
                           <warning descr="Type signature has too many arguments"># type: (Bar, int) -> None</warning>
                           pass

                       def egg11(self, a, b):
                           <warning descr="Type signature has too many arguments"># type: (Bar, int, str, bool) -> None</warning>
                           pass

                       def egg12(self, a, b):
                           <warning descr="Type signature has too few arguments"># type: (Bar) -> None</warning>
                           pass

                       # self is not specified
                       def spam2(self):
                           <warning descr="The type of self 'int' is not a supertype of its class 'Bar'"># type: (int) -> None</warning>
                           pass

                       def egg2(self, a, b):
                           <warning descr="The type of self 'int' is not a supertype of its class 'Bar'"># type: (int, str, bool) -> None</warning>
                           pass

                       # cls is not specified
                       @classmethod
                       def spam3(cls):
                           <warning descr="The type of self 'int' is not a supertype of its class 'type[Bar]'"># type: (int) -> None</warning>
                           pass

                       @classmethod
                       def egg3(cls, a, b):
                           <warning descr="The type of self 'int' is not a supertype of its class 'type[Bar]'"># type: (int, str, bool) -> None</warning>
                           pass

                       # cls is specified
                       @classmethod
                       def spam4(cls):
                           <warning descr="Type signature has too many arguments"># type: (Type[Bar], int) -> None</warning>
                           pass

                       @classmethod
                       def egg41(cls, a, b):
                           <warning descr="Type signature has too many arguments"># type: (Type[Bar], int, str, bool) -> None</warning>
                           pass

                       @classmethod
                       def egg42(cls, a, b):
                           <warning descr="Type signature has too few arguments"># type: (Type[Bar]) -> None</warning>
                           pass

                       @staticmethod
                       def spam5():
                           <warning descr="Type signature has too many arguments"># type: (int) -> None</warning>
                           pass

                       @staticmethod
                       def egg51(a, b):
                           <warning descr="Type signature has too many arguments"># type: (int, str, bool) -> None</warning>
                           pass

                       @staticmethod
                       def egg52(a, b):
                           <warning descr="Type signature has too few arguments"># type: (int) -> None</warning>
                           pass""")
  }

  @TestFor(issues = ["PY-20530"])
  fun `test typing member parameters`() {
    doTest(
      """
        from typing import Callable, List

        foo1: Callable[[int], <error descr="Parameters to generic types must be types">[int]</error>] = None
        foo2: Callable[[int], <error descr="Parameters to generic types must be types">[int, str]</error>] = None
        foo3: List[<error descr="Parameters to generic types must be types">[int]</error>]
        foo4: List[<error descr="Parameters to generic types must be types">[int, str]</error>]

        l1 = [int]
        l2 = [int, str]

        foo5: Callable[[int], <error descr="Parameters to generic types must be types">l1</error>] = None
        foo6: Callable[[int], <error descr="Parameters to generic types must be types">l2</error>] = None
        foo7: List[<error descr="Parameters to generic types must be types">l1</error>]
        foo8: List[<error descr="Parameters to generic types must be types">l2</error>]"""
    )
  }

  @TestFor(issues = ["PY-32530"])
  fun `test annotation and ignore comment`() {
    doTest("""
                   def foo(a: str):  # type: ignore
                       pass
                   def bar(a: Unknown):  # type: ignore[no-untyped-def, name-defined]
                       pass""")
  }

  fun `test annotating non-self attribute`() {
    doTest("""
                   class A:
                       def method(self, b):
                           <warning descr="Non-self attribute could not be type hinted">b.a</warning>: int = 1

                   class B:
                       pass

                   <warning descr="Non-self attribute could not be type hinted">B.a</warning>: str = "2"

                   def func(a):
                       <warning descr="Non-self attribute could not be type hinted">a.xxx</warning>: str = "2"
                   """)
  }

  @TestFor(issues = ["PY-35235"])
  fun `test typing extensions Literal`() {
    doTest("""
                   from typing_extensions import Literal, LiteralString

                   a: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">1 + 2</warning>]
                   b: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">4j</warning>]
                   c: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">3.14</warning>]
                   d: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">...</warning>]

                   class A:
                       pass

                   e: Literal[Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">A</warning>]]
                   f = Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">A</warning>]
                   g: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">f</warning>]
                   
                   h: Literal[-1]
                   i: Literal['abb']
                   j: Literal[False]
                   k: Literal[None]
                   l: Literal[Literal[-3]]
                   
                   ONE = Literal[1]
                   
                   m = Literal[ONE]
                   
                   def f(c: bool):
                       v: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">1 if c else 2</warning>]
                   
                   expr: LiteralString = "aba"
                   n: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">f"hello {expr}"</warning>]
                   """)
  }

  @TestFor(issues = ["PY-79227"])
  fun `test Enum Literal`() {
    doTest("""
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
                   
                   v1: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">A.X</warning>]
                   
                   X = Color.R
                   v2: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">X</warning>]
                   
                   v3: Literal[Color.G]
                   v4: Literal[Color.RED]
                   v5: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">Color.foo</warning>]
                   v6: Literal[Color.bar]
                   
                   v7: Literal[SuperEnum.PINK]
                   
                   v8: Literal[E.FOO]
                   v9: Literal[E.BAR]
                   v10: Literal[E.BUZ]
                   v11: Literal[E.QUX]
                   v12: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">E.meth2</warning>]""")
  }

  @TestFor(issues = ["PY-79227"])
  fun testEnumLiteralMultiFile() {
    doMultiFileTest()
  }

  @TestFor(issues = ["PY-35235"])
  fun `test Literal without arguments`() {
    doTest("""
                   from typing import Literal
                   a: <warning descr="'Literal' must have at least one parameter">Literal</warning> = 1
                   b = 2  # type: <warning descr="'Literal' must have at least one parameter">Literal</warning>""")
  }

  @TestFor(issues = ["PY-35235"])
  fun `test non-plain string as typing Literal index`() {
    doTest("from typing import Literal\n" +
           "a: Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">f\"1\"</warning>] = \"1\"")
  }

  fun `test parameterized builtin collections before 39`() {
    runWithLanguageLevel(LanguageLevel.PYTHON38) {
      doTest("""
                     xs: <warning descr="Builtin 'type' cannot be parameterized directly">type[str]</warning>
                     ys: <warning descr="Builtin 'tuple' cannot be parameterized directly">tuple[int, str]</warning>
                     zs: <warning descr="Builtin 'dict' cannot be parameterized directly">dict[int, str]</warning>""")
    }
  }

  @TestFor(issues = ["PY-42418"])
  fun `test parameterized builtin collections`() {
    doTest("""
                   xs: type[str]
                   ys: tuple[int, str]
                   zs: dict[int, str]""")
  }

  @TestFor(issues = ["PY-41847"])
  fun `test typing Annotated`() {
    doTest("""
                   from typing import Annotated

                   a: Annotated[<warning descr="'Annotated' must be called with at least two arguments">1</warning>]
                   b: Annotated[int, 1]
                   c: Annotated[<warning descr="'Annotated' must be called with at least two arguments">...</warning>]

                   class A:
                       pass

                   d: Annotated[A, '']
                   e: Annotated[<warning descr="'Annotated' must be called with at least two arguments">Annotated[A, True]</warning>]
                   f: Annotated[Annotated[<warning descr="'Annotated' must be called with at least two arguments">A</warning>], '']
                   g: Annotated[<error>[]</error>, 1]
                   """)
  }

  @TestFor(issues = ["PY-89188"])
  fun `test Annotated metadata`() {
    doTest(
      """
       from typing import Annotated

       a: Annotated[int, [], print("asdf")]
       """
    )
  }

  @TestFor(issues = ["PY-41847"])
  fun `test instance and class checks on Annotated`() {
    doTest("""
                   from typing import Annotated

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Annotated' cannot be used with instance and class checks">Annotated</error>)
                   B = Annotated
                   assert issubclass(A, <error descr="'Annotated' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), <error descr="'Annotated' cannot be used with instance and class checks">Annotated[1]</error>)
                   assert issubclass(A, <error descr="'Annotated' cannot be used with instance and class checks">B[1]</error>)
                   C = B[int, 2]
                   assert issubclass(A, <error descr="'Annotated' cannot be used with instance and class checks">C</error>)""")
  }

  @TestFor(issues = ["PY-41847"])
  fun `test Annotated without arguments`() {
    doTest("""
                   from typing import Annotated
                   a: <warning descr="'Annotated' must be called with at least two arguments">Annotated</warning> = 1
                   b = 2  # type: Annotated[<warning descr="'Annotated' must be called with at least two arguments">int</warning>]""")
  }

  @TestFor(issues = ["PY-42334"])
  fun `test parametrized TypeAlias in expression`() {
    doTest("""
                   from typing import TypeAlias

                   Alias = TypeAlias[<error descr="'TypeAlias' cannot be parameterized">int</error>]""")
  }


  @TestFor(issues = ["PY-42334"])
  fun `test parametrized TypeAlias in annotation`() {
    doTest("""
                   from typing import TypeAlias

                   Alias: <warning descr="'TypeAlias' must be used as standalone type hint">TypeAlias</warning>[int]""")
  }

  @TestFor(issues = ["PY-42334"])
  fun `test non-top-level TypeAlias`() {
    doTest("""
                   from typing import TypeAlias

                   Alias: Final[<warning descr="'TypeAlias' must be used as standalone type hint">TypeAlias</warning>] = str""")
  }

  @TestFor(issues = ["PY-42334"])
  fun `test not initialized TypeAlias`() {
    doTest("""
                   from typing import TypeAlias

                   <warning descr="Type alias must be immediately initialized">Alias</warning>: TypeAlias""")
  }

  @TestFor(issues = ["PY-42334"])
  fun `test not top-level TypeAlias`() {
    doTest("""
                   from typing import TypeAlias

                   def func():
                       <warning descr="Type alias must be top-level declaration">Alias</warning>: TypeAlias = str""")
  }

  @TestFor(issues = ["PY-46602"])
  fun `test no inspection TypedDict in Python 38`() {
    runWithLanguageLevel(LanguageLevel.PYTHON38) {
      doTest("""
                     from __future__ import annotations

                     def hello(i: dict[str, str]):
                         return i""")
    }
  }

  @TestFor(issues = ["PY-50401"])
  fun `test ParamSpec name as Literal`() {
    doTest("""
                   from typing import ParamSpec

                   name = 'T0'
                   T0 = ParamSpec(<warning descr="'ParamSpec()' expects a string literal as first argument">name</warning>)
                   T1 = ParamSpec('T1')""")
  }

  @TestFor(issues = ["PY-50401"])
  fun `test ParamSpec name and target name equality`() {
    doTest("""
                   from typing import ParamSpec

                   T0 = ParamSpec(<warning descr="The argument to 'ParamSpec()' must be a string equal to the variable name to which it is assigned">'T1'</warning>)
                   T1 = ParamSpec('T1')""")
  }

  @TestFor(issues = ["PY-50930"])
  fun `test no inspection in Callable parameter ParamSpec from typing extensions`() {
    doTest("""
                   from typing import Callable, TypeVar
                   from typing_extensions import ParamSpec
                   P = ParamSpec("P")
                   R = TypeVar("R")
                   def foo(it: Callable[P, R]) -> Callable[P, R]:
                       ...""")
  }

  @TestFor(issues = ["PY-53104"])
  fun `test instance and class checks on typing Self`() {
    doTest("""
                   from typing import Self


                   class A:
                       pass


                   class B:
                       def foo(self: Self):
                           assert isinstance(A(), <error descr="'Self' cannot be used with instance and class checks">Self</error>)
                           assert issubclass(A, <error descr="'Self' cannot be used with instance and class checks">Self</error>)
                   """)
  }

  @TestFor(issues = ["PY-53104"])
  fun `test typing Self subscription`() {
    doTest("""
                   from typing import Self, Generic, TypeVar

                   T = TypeVar("T")


                   class A(Generic[T]):
                       def foo(self):
                           x: Self[<error descr="'Self' cannot be parameterized">int</error>]
                   """)
  }

  @TestFor(issues = ["PY-53104"])
  fun `test typing Self annotation outside class`() {
    doTest("""
                   from typing import Self

                   def foo() -> <warning descr="Cannot use 'Self' outside class">Self</warning>:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-53104"])
  fun `test typing Self annotation for variable outside class`() {
    doTest("""
                   from typing import Self

                   something: <warning descr="Cannot use 'Self' outside class">Self</warning> | None = None
                   """)
  }

  @TestFor(issues = ["PY-53104"])
  fun `test typing Self in static method`() {
    doTest("""
                   from __future__ import annotations
                   from typing import Self

                   class SomeClass:
                       @staticmethod
                       def foo(bar: <warning descr="Cannot use 'Self' in staticmethod">Self</warning>) -> <warning descr="Cannot use 'Self' in staticmethod">Self</warning>:
                           return bar
                   """)
  }

  @TestFor(issues = ["PY-53104"])
  fun `test typing Self parameter has different annotation`() {
    doTest("""
                   from __future__ import annotations
                   from typing import Self

                   class SomeClass:
                       def foo(self: SomeClass, bar: <warning descr="Cannot use 'Self' if 'self' parameter is not 'Self' annotated">Self</warning>) -> <warning descr="Cannot use 'Self' if 'self' parameter is not 'Self' annotated">Self</warning>:
                           return self
                   """)
  }

  @TestFor(issues = ["PY-53104"])
  fun `test typing Self cls parameter has different annotation`() {
    doTest("""
                   from __future__ import annotations
                   from typing import Self

                   class SomeClass:
                       @classmethod
                       def foo(cls: SomeClass, bar: <warning descr="Cannot use 'Self' if 'cls' parameter is not 'Self' annotated">Self</warning>) -> <warning descr="Cannot use 'Self' if 'cls' parameter is not 'Self' annotated">Self</warning>:
                           return self
                   """)
  }

  @TestFor(issues = ["PY-53104"])
  fun `test typing Self in static method body`() {
    doTest("""
                   from typing import Self


                   class C:
                       @staticmethod
                       def m():
                           obj: <warning descr="Cannot use 'Self' in staticmethod">Self</warning> = None""")
  }

  @TestFor(issues = ["PY-53104"])
  fun `test typing Self in function body self parameter has different annotation`() {
    doTest("""
                   from typing import Self


                   class C:
                       def m(self: C):
                           obj: <warning descr="Cannot use 'Self' if 'self' parameter is not 'Self' annotated">Self</warning> = None
                   """)
  }

  @TestFor(issues = ["PY-62301"])
  fun `test typing Self in new method`() {
    doTest("""
                   from typing import Self
                   
                   class ReturnsSelf:
                       def __new__(cls, value: int) -> Self: ...
                   """)
  }

  @TestFor(issues = ["PY-36317"])
  fun `test dict subscription not reported as parametrized generic`() {
    doTest("""
                   keys_and_types = {
                       'comment': (str, type(None)),
                       'from_budget': (bool, type(None)),
                       'to_member': (int, type(None)),
                       'survey_request': (int, type(None)),
                   }
                                      
                   def type_is_valid(test_key, test_value):
                       return isinstance(test_value, keys_and_types[test_key])
                   """)
  }

  @TestFor(issues = ["PY-36317"])
  fun `test tuple subscription not reported as parametrized generic`() {
    doTest("""
                   tuple_of_types = (str, bool, int)
                   
                   def my_is_instance(value, index: int) -> bool:
                       return isinstance(value, tuple_of_types[index])
                   """)
  }

  @TestFor(issues = ["PY-36317"])
  fun `test plain dict type subscription not reported as parametrized generic`() {
    doTest("""
                   def foo(d: dict, s: dict):
                       for key in s.keys():
                           if not isinstance(d[key], s[key]):
                               raise TypeError
                   """)
  }

  @TestFor(issues = ["PY-53105"])
  fun `test no variadic Generic error in class declaration`() {
    doTest("""
                   from typing import Generic, TypeVarTuple

                   Shape = TypeVarTuple('Shape')


                   class Array(Generic[*Shape]):
                       ...
                   """)
  }

  @TestFor(issues = ["PY-53105"])
  fun `test TypeVarTuple name as Literal`() {
    doTest("""
                   from typing import TypeVarTuple

                   name = 'Ts'
                   Ts = TypeVarTuple(<warning descr="'TypeVarTuple()' expects a string literal as first argument">name</warning>)
                   Ts1 = TypeVarTuple('Ts1')""")
  }

  @TestFor(issues = ["PY-53105"])
  fun `test TypeVarTuple name and target name equality`() {
    doTest("""
                   from typing import TypeVarTuple

                   Ts = TypeVarTuple(<warning descr="The argument to 'TypeVarTuple()' must be a string equal to the variable name to which it is assigned">'T'</warning>)
                   Ts1 = TypeVarTuple('Ts1')""")
  }

  @TestFor(issues = ["PY-70528"])
  fun `test TypeVarTuple from typing extensions name and target name equality`() {
    doTest("""
                   from typing_extensions import TypeVarTuple
                   
                   Ts = TypeVarTuple(<warning descr="The argument to 'TypeVarTuple()' must be a string equal to the variable name to which it is assigned">'T'</warning>)
                   Ts1 = TypeVarTuple('Ts1')
                   """)
  }

  @TestFor(issues = ["PY-53105"])
  fun `test TypeVarTuple more than one unpacking`() {
    doTest("""
                    from typing import TypeVarTuple
                    from typing import Generic
                    
                    Ts1 = TypeVarTuple("Ts1")
                    Ts2 = TypeVarTuple("Ts2")
                    
                    
                    class Array(Generic[*Ts1, <error descr="Parameters to generic cannot contain more than one unpacking">*Ts2</error>]):
                        ...
                    """)
  }


  fun `test TypeIs does not match`() {
    doTest("""
                    from typing_extensions import TypeIs
                    
                    def <warning descr="Return type of TypeIs 'float | int' is not consistent with the type of the first parameter 'int'">foo</warning>(x: int) -> TypeIs[float]:
                        ...
                    
                    def bar(x: float) -> TypeIs[float]:
                        ...
                    
                    class A:
                        def <warning descr="Return type of TypeIs 'float | int' is not consistent with the type of the first parameter 'int'">f1</warning>(self, x: int) -> TypeIs[float]:
                            ...
                    
                        def f2(self, x: float) -> TypeIs[float]:
                            ...
                    
                        @classmethod
                        def <warning descr="Return type of TypeIs 'float | int' is not consistent with the type of the first parameter 'int'">f3</warning>(cls, x: int) -> TypeIs[float]:
                            ...
                    
                        @classmethod
                        def f4(cls, x: float) -> TypeIs[float]:
                            ...

                        @staticmethod
                        def <warning descr="Return type of TypeIs 'float | int' is not consistent with the type of the first parameter 'int'">f5</warning>(x: int) -> TypeIs[float]:
                            ...
                    
                        @staticmethod
                        def f6(x: float) -> TypeIs[float]:
                            ...
                    """)
  }

  fun `test TypeIs does not match 2`() {
    doTest("""
                   from typing_extensions import TypeIs
                   
                   class Base:
                       pass
                   
                   class Derived(Base):
                       pass
                   
                   def <warning descr="Return type of TypeIs 'Base' is not consistent with the type of the first parameter 'Derived'">isInt123</warning>(x: Derived) -> TypeIs[Base]:
                       ...
                   """)
  }

  fun `test TypeIs match`() {
    doTest("""
                   from typing_extensions import TypeIs
                   
                   class Base:
                       pass
                   
                   class Derived(Base):
                       pass
                   
                   def isInt123(x: Base) -> TypeIs[Derived]:
                       ...
                   """)
  }

  fun `test TypeIs missed parameter`() {
    doTest("""
                    from typing_extensions import TypeIs
                    
                    def <warning descr="User-defined TypeGuard or TypeIs functions must have at least one parameter">foo</warning>() -> TypeIs[float]:
                      ...
                    
                    class A:
                      def <warning descr="User-defined TypeGuard or TypeIs functions must have at least one parameter">foo</warning>(self) -> TypeIs[float]:
                        ...
                    
                      @classmethod
                      def <warning descr="User-defined TypeGuard or TypeIs functions must have at least one parameter">bar</warning>(cls) -> TypeIs[float]:
                        ...
                    
                      @staticmethod
                      def <warning descr="User-defined TypeGuard or TypeIs functions must have at least one parameter">buz</warning>() -> TypeIs[float]:
                        ...
                    """)
  }

  fun `test TypeGuard missed parameter`() {
    doTest("""
                    from typing import TypeGuard
                    
                    def <warning descr="User-defined TypeGuard or TypeIs functions must have at least one parameter">f1</warning>() -> TypeGuard[str]:
                        ...
                    
                    def f2(x: bool) -> TypeGuard[str]:
                        ...
                    
                    class A:
                        def <warning descr="User-defined TypeGuard or TypeIs functions must have at least one parameter">f1</warning>(self) -> TypeGuard[str]:
                            ...
                    
                        def f2(self, x: int) -> TypeGuard[str]:
                            ...
                    
                        @classmethod
                        def <warning descr="User-defined TypeGuard or TypeIs functions must have at least one parameter">f3</warning>(cls) -> TypeGuard[str]:
                            ...
                    
                        @classmethod
                        def f4(cls, x: float) -> TypeGuard[str]:
                            ...
                    
                        @staticmethod
                        def <warning descr="User-defined TypeGuard or TypeIs functions must have at least one parameter">f5</warning>() -> TypeGuard[str]:
                            ...
                    
                        @staticmethod
                        def f6(x: bool) -> TypeGuard[str]:
                            ...
                    """)
  }

  @TestFor(issues = ["PY-71002"])
  fun `test non-default TypeVars following ones with defaults`() {
    doTest("""
                   from typing import TypeVar, Generic
                   
                   DefaultT = TypeVar("DefaultT", default = str)
                   DefaultT1 = TypeVar("DefaultT1", default = int)
                   NoDefaultT2 = TypeVar("NoDefaultT2")
                   NoDefaultT3 = TypeVar("NoDefaultT3")
                   
                   class Clazz(Generic[DefaultT, DefaultT1, <error descr="Non-default TypeVars cannot follow ones with defaults">NoDefaultT2</error>]):
                       ...
                   class ClazzA(Generic[DefaultT1, <error descr="Non-default TypeVars cannot follow ones with defaults">NoDefaultT2</error>]):
                       ...
                   class ClazzB(Generic[DefaultT, <error descr="Non-default TypeVars cannot follow ones with defaults">NoDefaultT2</error>, DefaultT1]):
                       ...
                   class ClazzC(Generic[NoDefaultT2, NoDefaultT3]):
                       ...
                   """)
  }

  fun `test cast call`() {
    doTest("""
                   from typing import cast
                   
                   def f(val: object):
                       v0 = cast(int, 10) # ok
                       v1 = cast(list[int], val) # ok
                       v2 = cast('list[float]', val) # ok
                       v3 = cast(<warning descr="Expected a type">1</warning>, val)
                   """)
  }

  fun `test isinstance and issubclass on NewType`() {
    doTest("""
                   from typing import NewType
                   
                   UserId = NewType("UserId", int)

                   def f(val):
                       isinstance(val, <error descr="NewType type cannot be used with instance and class checks">UserId</error>)
                       issubclass(int, <error descr="NewType type cannot be used with instance and class checks">UserId</error>)
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVar defaults scoping`() {
    doTest("""
                   from typing import TypeVar, Generic
                   
                   S1 = TypeVar("S1")
                   S2 = TypeVar("S2", default=S1)
                   StepT = TypeVar("StepT", default=int | None)
                   StartT = TypeVar("StartT", default="StopT")
                   StopT = TypeVar("StopT", default=int)
                   
                   class slice(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">StartT</warning>, StopT, StepT]): ...
                   class slice2(Generic[StopT, StartT, StepT]): ...
                   
                   class Foo3(Generic[S1]):
                       class Bar2(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">S2</warning>]): ...
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test ParamSpec default scoping`() {
    doTest("""
                   from typing import ParamSpec, Generic
                   
                   P1 = ParamSpec("P1", default=[int, str])
                   P2 = ParamSpec("P2", default=P1)
                   
                   class Clazz(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">P2</warning>, P1]): ...
                   """)
  }

  @TestFor(issues = ["PY-71002"])
  fun `test non-default ParamSpec following ones with defaults`() {
    doTest("""
                   from typing import ParamSpec, Generic
                   
                   P1 = ParamSpec("P1")
                   P2 = ParamSpec("P2", default=[int, str])
                   
                   class Clazz(Generic[P2, <error descr="Non-default TypeVars cannot follow ones with defaults">P1</error>]): ...
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVarTuple default scoping`() {
    doTest("""
                   from typing import Generic, TypeVarTuple, Unpack
                   
                   Ts1 = TypeVarTuple("Ts1", default=Unpack[tuple[int, int]])
                   Ts2 = TypeVarTuple("Ts2", default=Unpack[Ts1])
                   
                   class Clazz(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">*Ts2</warning>]): ...
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVar allows default values`() {
    doTest("""
                   from typing import TypeVar, Generic
                   
                   T = TypeVar("T", default=<warning descr="Default type must be a type expression">3</warning>)
                   T1 = TypeVar("T1", default=<warning descr="Default type must be a type expression">True</warning>)
                   T3 = TypeVar("T3", default="NormalT")
                   NormalT = TypeVar("NormalT")
                   T4 = TypeVar("T4", default=NormalT)
                   T5 = TypeVar("T5", default=list)
                   class Clazz: ...
                   T6 = TypeVar("T6", default=Clazz)
                   T7 = TypeVar("T7", default=<warning descr="Default type must be a type expression">[int]</warning>)
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test new-style TypeVar allows default values`() {
    doTest("""
                   from typing import TypeVar, Generic, ParamSpec, TypeVarTuple
                   T1 = TypeVar("T1")
                   Ts1 = TypeVarTuple("Ts1")
                   P1 = ParamSpec("P1")
                   
                   class Clazz[T = int]: ...
                   class Clazz[T = dict[int, str]]: ...
                   class Clazz[T, T1 = T]: ...
                   class Clazz[T = <warning descr="Default type must be a type expression">1</warning>]: ...
                   class Clazz[T = <warning descr="Default type must be a type expression">True</warning>]: ...
                   class Clazz[T = <warning descr="'TypeVarTuple' cannot be used in default type of TypeVar">Ts1</warning>]: ...
                   class Clazz[T = <warning descr="'ParamSpec' cannot be used in default type of TypeVar">P1</warning>]: ...
                   class Clazz[T = <warning descr="Default type must be a type expression">[int]</warning>]: ...
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test ParamSpec allows default values`() {
    doTest("""
                   from typing import ParamSpec, TypeVar
                   T = TypeVar(<warning descr="The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned">"T1"</warning>)
                   P = ParamSpec(<warning descr="The argument to 'ParamSpec()' must be a string equal to the variable name to which it is assigned">"P1"</warning>)
                   
                   P1 = ParamSpec("P1", default=[])
                   P2 = ParamSpec("P2", default=[int, str, None, int | None])
                   P3 = ParamSpec("P3", default=[int, T])
                   P4 = ParamSpec("P4", default=[int])
                   P5 = ParamSpec("P5", default=...)
                   P6 = ParamSpec("P6", default=<warning descr="Default type of ParamSpec must be a ParamSpec type or a list of types">int</warning>)
                   P7 = ParamSpec("P7", default=<warning descr="Default type of ParamSpec must be a ParamSpec type or a list of types">3</warning>)
                   P8 = ParamSpec("P8", default=<warning descr="Default type of ParamSpec must be a ParamSpec type or a list of types">(1, int)</warning>)
                   P9 = ParamSpec("P9", default=P)
                   P10 = ParamSpec("P10", default=[<warning descr="Default type must be a type expression">1</warning>, <warning descr="Default type must be a type expression">2</warning>])
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test new-style ParamSpec allows default values`() {
    doTest("""
                   from typing import TypeVar, Generic, ParamSpec, TypeVarTuple
                   T1 = TypeVar("T1")
                   Ts1 = TypeVarTuple("Ts1")
                   P1 = ParamSpec("P1")
                   
                   class Clazz[**P = []]: ...
                   class Clazz[**P = [int]]: ...
                   class Clazz[**P = [int, str]]: ...
                   class Clazz[**P = [int, <warning descr="Default type must be a type expression">3</warning>]]: ...
                   class Clazz[**P = [int, <warning descr="Default type must be a type expression">True</warning>]]: ...
                   class Clazz[**P = <warning descr="Default type of ParamSpec must be a ParamSpec type or a list of types">True</warning>]: ...
                   class Clazz[**P = <warning descr="Default type of ParamSpec must be a ParamSpec type or a list of types">T1</warning>]: ...
                   class Clazz[**P = <warning descr="Default type of ParamSpec must be a ParamSpec type or a list of types">Ts1</warning>]: ...
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVarTuple allows default values`() {
    doTest("""
                   from typing import TypeVarTuple, Unpack, TypeVar
                   
                   T = TypeVar("T")
                   Ts0 = TypeVarTuple("Ts0")
                   Ts1 = TypeVarTuple("Ts1", default=Unpack[tuple[int]])
                   Ts2 = TypeVarTuple("Ts2", default=<warning descr="Default type of TypeVarTuple must be unpacked">tuple[int]</warning>)
                   Ts3 = TypeVarTuple("Ts3", default=<warning descr="Default type of TypeVarTuple must be unpacked">int</warning>)
                   Ts4 = TypeVarTuple("Ts4", default=Unpack[Ts0])
                   Ts5 = TypeVarTuple("Ts5", default=<warning descr="Default type of TypeVarTuple must be unpacked">Ts0</warning>)
                   Ts6 = TypeVarTuple("Ts6", default=Unpack[tuple[int, ...]])
                   Ts7 = TypeVarTuple("Ts7", default=Unpack[tuple[T, T]])
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test new-style TypeVarTuple allows default values`() {
    doTest("""
                   from typing import TypeVar, Generic, ParamSpec, TypeVarTuple, Unpack

                   class Clazz[T1, *Ts = Unpack[tuple[int, T1]]]: ...
                   class Clazz[*Ts = <warning descr="Default type of TypeVarTuple must be unpacked">1</warning>]: ...
                   class Clazz[*Ts = <warning descr="Default type of TypeVarTuple must be unpacked">True</warning>]: ...
                   class Clazz[*Ts = <warning descr="Default type of TypeVarTuple must be unpacked">tuple[int]</warning>]: ...
                   class Clazz[*Ts = *tuple[int]]: ...
                   class Clazz[*Ts = Unpack[tuple[int]]]: ...
                   class Clazz[T1, *Ts = <warning descr="Default type of TypeVarTuple must be unpacked">T1</warning>]: ...
                   class Clazz[*Ts1, *Ts = <warning descr="Default type of TypeVarTuple must be unpacked">Ts1</warning>]: ...
                   class Clazz[**P1, *Ts = <warning descr="Default type of TypeVarTuple must be unpacked">P1</warning>]: ...
                   class Clazz[*Ts = Unpack[tuple[int, ...]]]: ...
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVar can not follow TypeVarTuple`() {
    doTest("""
                   from typing import TypeVar, Generic, ParamSpec, TypeVarTuple, Unpack
                   T = TypeVar("T", default = int)
                   Ts = TypeVarTuple("Ts")
                   TsDef = TypeVarTuple("TsDef", default = Unpack[tuple[int, int]])
                   P = ParamSpec("P", default = [str, bool])
                   
                   class Clazz(Generic[Ts, <error descr="TypeVar with a default value cannot follow TypeVarTuple">T</error>]): ...
                   class Clazz1(Generic[TsDef, <error descr="TypeVar with a default value cannot follow TypeVarTuple">T</error>]): ...
                   class Clazz2(Generic[TsDef, P]): ...
                   class Clazz3(Generic[Ts, P]): ...
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVar parameterization with defaults`() {
    doTest("""
                   from typing import TypeVar, Generic
                   
                   DefaultT = TypeVar("DefaultT", default = str)
                   DefaultT1 = TypeVar("DefaultT1", default = int)
                   NoDefaultT2 = TypeVar("NoDefaultT2")
                   NoDefaultT3 = TypeVar("NoDefaultT3")
                   NoDefaultT4 = TypeVar("NoDefaultT4")
                   
                   class Clazz(Generic[NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1]): ...
                   
                   c1 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int</warning>]()
                   c2 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int, str</warning>]()
                   c3 = Clazz[int, str, bool]()
                   c4 = Clazz[int, str, bool, int]()
                   c5 = Clazz[int, str, bool, int, str]()
                   c6 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int, str, bool, int, str, int</warning>]()
                   c7 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int, str, bool, int, str, int, int</warning>]()
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVar parameterization explicit Any in defaults`() {
    doTest("""
                   from typing import Generic, TypeVar, Any
                   
                   T = TypeVar('T')
                   T1 = TypeVar('T1', default=Any)
                   T2 = TypeVar('T2', default=Any)
                   
                   class Clazz(Generic[T, T1, T2]): ...
                   
                   c = Clazz[int]()
                   c1 = Clazz[int, str]()
                   c2 = Clazz[int, str, bool]()
                   c3 = Clazz[<warning descr="Passed type arguments do not match type parameters [T, T1, T2] of class 'Clazz'">int, str, bool, float</warning>]()
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVarTuple parameterization with defaults`() {
    doTest("""
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
  }

  @TestFor(issues = ["PY-75759"])
  fun `test default ParamSpec following TypeVarTuple`() {
    doTest("""
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
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVar and TypeVarTuple parameterization with defaults`() {
    doTest("""
                   from typing import TypeVar, Generic, TypeVarTuple, Unpack
                   
                   DefaultT = TypeVar("DefaultT", default = str)
                   DefaultT1 = TypeVar("DefaultT1", default = int)
                   NoDefaultT2 = TypeVar("NoDefaultT2")
                   NoDefaultT3 = TypeVar("NoDefaultT3")
                   DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[int, str]])
                   
                   
                   class Clazz(Generic[NoDefaultT2, NoDefaultT3, DefaultT, DefaultT1, DefaultTs]):
                       ...
                   
                   c1 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, DefaultT, DefaultT1, *DefaultTs] of class 'Clazz'">int</warning>]()
                   c2 = Clazz[int, str]()
                   c3 = Clazz[int, str, bool]()
                   c4 = Clazz[int, str, bool, int]()
                   c5 = Clazz[int, str, bool, int, str]()
                   c6 = Clazz[int, str, bool, int, str, int]()
                   c7 = Clazz[int, str, bool, int, str, int, int]()
                   c8 = Clazz[int, str, bool, int, str, int, int, float]()
                   c9 = Clazz[int, str, bool, int, str, int, int, float, list]()
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test non-default type variables following ones with defaults new-style`() {
    doTest("""
                   class Clazz[NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT = int, DefaultT1 = str]:
                       ...
                   
                   c1 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int</warning>]()
                   c2 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int, str</warning>]()
                   c3 = Clazz[int, str, bool]()
                   c4 = Clazz[int, str, bool, int]()
                   c5 = Clazz[int, str, bool, int, str]()
                   c6 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int, str, bool, int, str, int</warning>]()
                   c7 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int, str, bool, int, str, int, int</warning>]()
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test type parameters out of scope not reported multiple times`() {
    doTest("""
                   from typing import TypeVar, Generic
                   
                   T1 = TypeVar('T1')
                   T2 = TypeVar('T2')
                   T3 = TypeVar('T3', default=T1 | T2)
                   T4 = TypeVar('T4', default=T2)
                   
                   class Clazz1(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">T3</warning>]): ...
                   class Clazz2(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">T3</warning>, <warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">T4</warning>]): ...
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVar default types are TypeVars`() {
    doTest("""
                   from typing import TypeVar, TypeVarTuple, ParamSpec
                   
                   type A1[**P, T = <warning descr="'ParamSpec' cannot be used in default type of TypeVar">P</warning>] = tuple[P, T]  # false negative
                   
                   Ts = TypeVarTuple("Ts")
                   T1 = TypeVar("T1", default=<warning descr="'TypeVarTuple' cannot be used in default type of TypeVar">Ts</warning>)
                   
                   P = ParamSpec("P")
                   T2 = TypeVar("T2", default=<warning descr="'ParamSpec' cannot be used in default type of TypeVar">P</warning>)
                   T3 = TypeVar("T3", default=dict[<warning descr="Passed type arguments do not match type parameters [_KT, _VT] of class 'dict'">str, Ts</warning>])
                   T4 = TypeVar("T4", default=dict[list[<warning descr="Passed type arguments do not match type parameters [_T] of class 'list'">P</warning>], str])
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test TypeVarTuple is not considered mandatory type parameter`() {
    doTest("""
                   class Clazz[T1, T2, *Ts, T3]: ...
                   
                   c1 = Clazz[<warning descr="Passed type arguments do not match type parameters [T1, T2, *Ts, T3] of class 'Clazz'">int</warning>]()
                   c2 = Clazz[<warning descr="Passed type arguments do not match type parameters [T1, T2, *Ts, T3] of class 'Clazz'">int, str</warning>]()
                   c3 = Clazz[int, str, bool]()
                   c4 = Clazz[int, str, bool, float]()
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test allowed type arguments`() {
    doTest("""
                   from typing import Literal, TypeAlias
                   
                   class Clazz[T1, T2 = int]: ...
                   
                   var = 1
                   myInt = int
                   type myIntOrStr = int | str
                   myIntAlias: TypeAlias = int
                   
                   class A:...
                   
                   c1 = Clazz[<error descr="Invalid type argument">print()</error>, int]()
                   c2 = Clazz[int, <error descr="Invalid type argument">print()</error>]()
                   c3 = Clazz[<error descr="Invalid type argument">1</error>]
                   c4 = Clazz["int", "str"]
                   c5 = Clazz[dict[int, str]]
                   c7 = Clazz[<error descr="Invalid type argument">True</error>]
                   c8 = Clazz[<error descr="Invalid type argument">list or set</error>]
                   c9 = Clazz[Literal[3]]
                   c10 = Clazz[<error descr="Parameters to generic types must be types">var</error>]
                   c11 = Clazz[myInt]
                   c12 = Clazz[myIntOrStr]
                   c13 = Clazz[myIntAlias]
                   c14 = Clazz[A]
                   c15 = Clazz[<error descr="Invalid type argument">{"a": "b"}</error>]
                   c16 = Clazz[<error descr="Invalid type argument">(lambda: int)()</error>]
                   c17 = Clazz[(int, str)]
                   """)
  }

  @TestFor(issues = ["PY-77601", "PY-76840"])
  fun `test ParamSpec not mapped to single type without square brackets`() {
    doTest("""
                   from typing import Generic, ParamSpec, TypeVar
                   
                   T = TypeVar("T")
                   P1 = ParamSpec("P1")
                   P2 = ParamSpec("P2")
                   
                   class ClassA(Generic[T, P1]): ...
                   
                   x: ClassA[<warning descr="Passed type arguments do not match type parameters [T, **P1] of class 'ClassA'">int, int</warning>]
                   x1: ClassA[int, [int]]
                   """)
  }

  @TestFor(issues = ["PY-75759"])
  fun `test ellipsis not reported`() {
    doTest("""
                   from typing import Generic, ParamSpec, TypeVar, Callable
                   T = TypeVar("T")
                   P1 = ParamSpec("P1")
                   
                   class ClassA(Generic[T, P1]):
                       ...
                   
                   def func23(x: ClassA[int, ...]) -> str:  # OK
                       return ""
                   """)
  }

  @TestFor(issues = ["PY-79693"])
  fun `test Never and NoReturn not reported as invalid type args`() {
    doTest("""
                   from typing import Never, NoReturn
                   
                   class ClassA:
                      a: NoReturn
                      b: list[NoReturn]
                      c: Never
                      d: list[Never]
                   """)
  }

  @TestFor(issues = ["PY-76820"])
  fun `test explicit generic TypeAlias parameterization number of type parameters one TypeVar`() {
    doTest("""
                   from typing import TypeAlias, TypeVar
                   
                   T = TypeVar("T")
                   
                   alias: TypeAlias = list[T]
                   alias2: TypeAlias = list[T] | set[T] | T
                   
                   a1: alias[int]
                   a2: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str</warning>]
                   a3: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str, bool</warning>]
                   a4: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str, bool, int</warning>]
                   
                   a21: alias2[int]
                   a22: alias2[<warning descr="Passed type arguments do not match type parameters of type alias 'alias2'">int, str</warning>]
                   a23: alias2[<warning descr="Passed type arguments do not match type parameters of type alias 'alias2'">int, str, bool</warning>]
                   a24: alias2[<warning descr="Passed type arguments do not match type parameters of type alias 'alias2'">int, str, bool, int</warning>]
                   """)
  }

  @TestFor(issues = ["PY-76820"])
  fun `test explicit generic TypeAlias parameterization number of type parameters two TypeVars`() {
    doTest("""
                   from typing import TypeAlias, TypeVar
                   
                   T = TypeVar("T")
                   U = TypeVar("U")
                   
                   alias: TypeAlias = dict[T, U]
                   a1: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int</warning>]
                   a2: alias[int, str]
                   a3: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str, bool</warning>]
                   a4: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str, bool, int</warning>]
                   """)
  }

  @TestFor(issues = ["PY-76820"])
  fun `test explicit generic TypeAlias parameterization number of type parameters multiple TypeVars`() {
    doTest("""
                   from typing import TypeAlias, TypeVar, Generic
                   
                   T = TypeVar("T")
                   T1 = TypeVar("T1")
                   T2 = TypeVar("T2")
                   T3 = TypeVar("T3")
                   
                   class Clazz(Generic[T3]): ...
                   
                   alias: TypeAlias = dict[T, list[T1]] | T2 | Clazz[T3]
                   
                   a1: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int</warning>]
                   a2: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str</warning>]
                   a3: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str, bool</warning>]
                   a4: alias[int, str, bool, int]
                   a5: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str, bool, int, float</warning>]
                   a5: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str, bool, int, float, str</warning>]
                   """)
  }

  @TestFor(issues = ["PY-76820"])
  fun `test explicit generic TypeAlias parameterization number of type parameters two TypeVars one has default`() {
    doTest("""
                   from typing import TypeAlias, TypeVar
                   
                   T = TypeVar("T")
                   U = TypeVar("U", default=str)
                   
                   alias: TypeAlias = dict[T, U]
                   a1: alias[int]
                   a2: alias[int, str]
                   a3: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str, bool</warning>]
                   a4: alias[<warning descr="Passed type arguments do not match type parameters of type alias 'alias'">int, str, bool, int</warning>]
                   
                   """)
  }

  @TestFor(issues = ["PY-76820"])
  fun `test explicit TypeAlias parameterization conformance tests`() {
    doTest("""
                   from typing import TypeAlias as TA
                   from typing import TypeVar, Callable
                   T = TypeVar("T")
                   
                   GoodTypeAlias2: TA = int | None
                   GoodTypeAlias3: TA = list[GoodTypeAlias2]
                   GoodTypeAlias4: TA = list[T]
                   GoodTypeAlias8: TA = Callable[[int, T], T]
                   
                   p1: GoodTypeAlias2[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   p2: GoodTypeAlias3[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   p3: GoodTypeAlias4[<warning descr="Passed type arguments do not match type parameters of type alias 'GoodTypeAlias4'">int, int</warning>]
                   p4: GoodTypeAlias8[<warning descr="Passed type arguments do not match type parameters of type alias 'GoodTypeAlias8'">int, int</warning>]
                   
                   ListAlias: TA = list
                   ListOrSetAlias: TA = list | set
                   
                   x2: ListAlias[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   x4: ListOrSetAlias[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   """)
  }

  @TestFor(issues = ["PY-76839"])
  fun `test implicit type alias already parameterized`() {
    doTest("""
                   alias = list
                   alias2 = list[int]
                   a1: alias[int]  # OK
                   a2: alias2[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   """)
  }

  @TestFor(issues = ["PY-76820"])
  // The behaviour for the explicit type aliases differs, see the test above
  fun `test explicit TypeAlias already parameterized`() {
    doTest("""
                   from typing import TypeAlias
                   
                   alias: TypeAlias = list
                   alias2: TypeAlias = list[int]
                   a1: alias[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   a2: alias2[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   """)
  }

  @TestFor(issues = ["PY-76839"])
  fun `test implicit type alias parameterization union`() {
    doTest("""
                   ListOrSetAlias = list | set
                   x: ListOrSetAlias[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   """)
  }

  @TestFor(issues = ["PY-76839"])
  fun `test implicit type alias parameterization conformance tests`() {
    doTest("""
                   from typing import TypeAlias as TA
                   from typing import TypeVar, Callable
                   
                   T = TypeVar("T")
                   
                   GoodTypeAlias2 = int | None
                   GoodTypeAlias3 = list[GoodTypeAlias2]
                   GoodTypeAlias4 = list[T]
                   GoodTypeAlias8 = Callable[[int, T], T]
                   
                   p1: GoodTypeAlias2[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   p2: GoodTypeAlias3[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   p3: GoodTypeAlias4[<warning descr="Passed type arguments do not match type parameters of type alias 'GoodTypeAlias4'">int, int</warning>]
                   p4: GoodTypeAlias8[<warning descr="Passed type arguments do not match type parameters of type alias 'GoodTypeAlias8'">int, int</warning>]
                   
                   ListAlias = list
                   ListOrSetAlias = list | set
                   x1: list[str] = ListAlias()
                   x2 = ListAlias[int]()
                   x4: ListOrSetAlias[<warning descr="Type alias is not generic or already specialized">int</warning>]
                   """)
  }

  @TestFor(issues = ["PY-76820"])
  fun `test explicit TypeAlias invalid values conformance tests`() {
    doTest("""
                   from typing import TypeAlias as TA
                   
                   var1 = 3
                   
                   def foo(): ...
                   
                   BadTypeAlias1: TA = <warning descr="Assigned value of type alias must be a correct type">eval(<warning descr="Generics should be specified through square brackets">"".join(<warning descr="Generics should be specified through square brackets">map(chr, [105, 110, 116])</warning>)</warning>)</warning>
                   BadTypeAlias2: TA = <warning descr="Assigned value of type alias must be a correct type">[int, str]</warning>
                   BadTypeAlias3: TA = <warning descr="Assigned value of type alias must be a correct type">((int, str),)</warning>
                   BadTypeAlias4: TA = <warning descr="Assigned value of type alias must be a correct type">[int for i in <warning descr="Generics should be specified through square brackets">range(1)</warning>]</warning>
                   BadTypeAlias5: TA = <warning descr="Assigned value of type alias must be a correct type">{"a": "b"}</warning>
                   BadTypeAlias6: TA = <warning descr="Assigned value of type alias must be a correct type">(lambda: int)()</warning>
                   BadTypeAlias7: TA = <warning descr="Assigned value of type alias must be a correct type">[int][0]</warning>
                   BadTypeAlias8: TA = <warning descr="Assigned value of type alias must be a correct type">int if 1 < 3 else str</warning>
                   BadTypeAlias9: TA = <warning descr="Assigned value of type alias must be a correct type">var1</warning>
                   BadTypeAlias10: TA = <warning descr="Assigned value of type alias must be a correct type">True</warning>
                   BadTypeAlias11: TA = <warning descr="Assigned value of type alias must be a correct type">1</warning>
                   BadTypeAlias12: TA = <warning descr="Assigned value of type alias must be a correct type">list or set</warning>
                   BadTypeAlias13: TA = <warning descr="Assigned value of type alias must be a correct type">f"{'int'}"</warning>
                   BadTypeAlias14: TA = <warning descr="Assigned value of type alias must be a correct type">f"int"</warning>
                   BadTypeAlias15: TA = <warning descr="Assigned value of type alias must be a correct type">u"int"</warning>
                   BadTypeAlias16: TA = <warning descr="Assigned value of type alias must be a correct type">b"int"</warning>
                   BadTypeAlias17: TA = <warning descr="Assigned value of type alias must be a correct type">"foo()"</warning>
                   """)
  }

  @TestFor(issues = ["PY-76820"])
  fun `test explicit TypeAlias valid values conformance tests`() {
    doTest("""
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
  }

  @TestFor(issues = ["PY-76820"])
  // Duplicates the test from conformance test suite but in multi-file context
  fun testExplicitTypeAliasesMultiFile() {
    doMultiFileTest()
  }

  @TestFor(issues = ["PY-76839"])
  // Duplicates the test from conformance test suite but in multi-file context
  fun testImplicitTypeAliasesMultiFile() {
    doMultiFileTest()
  }

  @TestFor(issues = ["PY-76839"])
  fun `test implicit type alias assignment chain`() {
    doTest("""
                 a1 = 3
                 a2 = a1
                 a3 = a2
                 def foo(p: <warning descr="Invalid type annotation">a3</warning>): ...
                 """)
  }

  fun `test multi-line type hint`() {
    doTest("""
                 value: $TRIPLE_QUOTE
                     int |
                     str |
                     list[int]
                 $TRIPLE_QUOTE
                 """)
  }

  fun `test ParamSpec args kwargs is valid`() {
    doTest("""
                   from typing import ParamSpec, Protocol
                   P = ParamSpec("P")
                   class Proto4(Protocol[P]):
                       def __call__(self, a: int, *args: P.args, **kwargs: P.kwargs) -> None: ...
                   """)
  }

  @TestFor(issues = ["PY-76834"])
  fun testTypeExprValidAnnotationsConformanceTestsSuite() {
    doTest()
  }

  @TestFor(issues = ["PY-76834"])
  fun testTypeExprInvalidAnnotationsConformanceTestsSuite() {
    doTest()
  }

  @TestFor(issues = ["PY-61787"])
  fun `test Concatenate not reported in Callable arguments`() {
    doTest("""
                   from typing import ParamSpec, Concatenate, Any, TypeVar, Callable
                   
                   P = ParamSpec('P')
                   T = TypeVar('T')
                   
                   def changing_signature(f: Callable[P, T]) -> Callable[Concatenate[Any, P], T]:  # no warnings expected
                       ...
                   """)
  }

  fun `test class is already parameterized`() {
    doTest("""
                   from typing import Generic, TypeVar
                   
                   DefaultStrT = TypeVar("DefaultStrT", default=str)
                   T = TypeVar("T")
                   T1 = TypeVar("T1")
                   
                   class Base(Generic[T, T1, DefaultStrT]): ...
                   class Foo(Base[int, float]): ...
                   foo = Foo[<warning descr="Class 'Foo' is already parameterized">int</warning>]()
                   
                   class Bar(Generic[T]): ...
                   class Baz(Bar[int]): ...
                   baz = Baz[<warning descr="Class 'Baz' is already parameterized">int</warning>]()
                   
                   class NoErr(Bar[int]):
                        def __class_getitem__(cls, item) -> str:
                            return "str"
                   
                   n = NoErr[int]()
                   """)
  }

  @TestFor(issues = ["PY-76894"])
  fun `test raw Concatenate usage`() {
    doTest("""
                   from typing import ParamSpec, Concatenate, Any, TypeVar, Callable, TypeAlias

                   P = ParamSpec('P')
                   T = TypeVar('T')

                   # Raw Concatenate in function parameters is not allowed
                   def func1(x: <warning descr="'Concatenate' can only be used as the first argument to 'Callable' in this context">Concatenate[int, P]</warning>) -> int:
                       ...
                   
                   var: <warning descr="'Concatenate' can only be used as the first argument to 'Callable' in this context">Concatenate[int, <warning descr="Unbound type variable">P</warning>]</warning>
                   
                   def return_concat() -> <warning descr="'Concatenate' can only be used as the first argument to 'Callable' in this context">Concatenate[int, P]</warning>:
                      ...

                   # Concatenate in type alias is allowed
                   ConcatenateAlias = Concatenate[int, P]
                   ConcatenateAlias2: TypeAlias = Concatenate[int, P]
                   type ConcatenateAlias3[**P] = Concatenate[int, P]

                   # Concatenate in Callable is allowed
                   def changing_signature(f: Callable[P, T]) -> Callable[Concatenate[Any, P], T]:
                       ...
                   """)
  }

  @TestFor(issues = ["PY-80248"])
  fun `test reference to type statement is valid type hint`() {
    doTest("""
                type my_type = str
                
                def func1(x: my_type) -> str: ...
                """)
  }

  @TestFor(issues = ["PY-80278"])
  fun `test reference to namedtuple is valid type hint`() {
    doTest("""
               from collections import namedtuple
               
               Instruction = namedtuple("Instruction", ["register", "op", "value", "base", "check", "limit"])
               
               def foo() -> Instruction:  # No warning expected
                   return Instruction(1, 2, 3, 4, 5, 6)
               """)
  }

  fun `test unresolved reference not reported as invalid type argument`() {
    doTest("""
               from missing_module import SomeType  # type: ignore
               
               def func4(some_type_tuple: tuple[SomeType, ...]):
                   pass
               
               class Clazz[T, T1]: ...
               
               c = Clazz[RefToNoWhere, WrongRef]() # will be reported by PyUnresolvedReferencesInspection, but not here
               """)
  }

  @TestFor(issues = ["PY-76862"])
  fun `test unions in type annotations with multiple elements`() {
    doTest("""
               bad1: <error descr="Union type annotations with forward references must be wrapped in quotes entirely">"ClassA"</error> | int
               bad2: int | <error descr="Union type annotations with forward references must be wrapped in quotes entirely">"ClassA"</error>
               bad3: int | str | bool | <error descr="Union type annotations with forward references must be wrapped in quotes entirely">"ClassA"</error>
               bad4: int | <error descr="Union type annotations with forward references must be wrapped in quotes entirely">"ClassA"</error> | str | bool
               good1: int | list["ClassA"]
               class ClassA: ...
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default can be subclass of bound`() {
    doTest("""
               from typing import TypeVar, List
               
               T1 = TypeVar('T1', bound=int, default=bool)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default can not be subclass of constraint`() {
    doTest("""
               from typing import TypeVar, List
               
               T1 = TypeVar('T1', int, str, default=<warning descr="Default type of TypeVar must be one of the constraint types">bool</warning>)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default type matches constraints`() {
    doTest("""
               from typing import TypeVar, List
               
               # Default type matches one of the constraints
               T1 = TypeVar('T1', str, int, default=str)
               T2 = TypeVar('T2', str, int, default=int)
               
               # Default type doesn't match any of the constraints
               T3 = TypeVar('T3', str, int, default=<warning descr="Default type of TypeVar must be one of the constraint types">bool</warning>)
               T4 = TypeVar('T4', str, int, default=<warning descr="Default type of TypeVar must be one of the constraint types">List[int]</warning>)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default type referring to TypeVar matches bound`() {
    doTest("""
               from typing import TypeVar
               
               Y1 = TypeVar("Y1", bound=int)
               Invalid = TypeVar("Invalid", float, str, default=<warning descr="Default type of TypeVar must be one of the constraint types">Y1</warning>)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default type referring to TypeVar matches constraints`() {
    doTest("""
               from typing import TypeVar
               
               Y1 = TypeVar("Y1", int, str)
               AlsoOk2 = TypeVar("AlsoOk2", int, str, bool, default=Y1)  # OK
               AlsoInvalid2 = TypeVar("AlsoInvalid2", bool, complex, default=<warning descr="Default type of TypeVar must be one of the constraint types">Y1</warning>)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default type referring to TypeVar without constraints`() {
    doTest("""
               from typing import TypeVar
               T = TypeVar("T")
               Invalid = TypeVar("Invalid", str, int, default=<warning descr="Default type of TypeVar must be one of the constraint types">T</warning>)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default bound matched against bound`() {
    doTest("""
               from typing import TypeVar
               
               X1 = TypeVar("X1", bound=int)
               Ok1 = TypeVar("Ok1", default=X1, bound=float)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default bound not matched against constraints`() {
    doTest("""
               from typing import TypeVar
               
               Y3 = TypeVar("Y3", bound=int)
               Invalid3 = TypeVar("Invalid3", str, complex, default=<warning descr="Default type of TypeVar must be one of the constraint types">Y3</warning>)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default bound not matched against bound`() {
    doTest("""
               from typing import TypeVar
               
               X1 = TypeVar("X1", bound=int)
               Invalid1 = TypeVar("Invalid1", default=<warning descr="Default type of TypeVar is not a subtype of the bound">X1</warning>, bound=str)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default constraints not matched against bound`() {
    doTest("""
               from typing import TypeVar
               
               Y4 = TypeVar("Y4", int, str)
               Invalid4 = TypeVar("Invalid4", bound=str, default=<warning descr="Default type of TypeVar is not a subtype of the bound">Y4</warning>)
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default checks new syntax`() {
    doTest("""
               # NOT OK
               def foo1[T1: int = <warning descr="Default type of TypeVar is not a subtype of the bound">str</warning>](): ...
               def foo2[T1: (int, bool) = <warning descr="Default type of TypeVar must be one of the constraint types">str</warning>](): ...
               def foo3[T1: int, T2: str = <warning descr="Default type of TypeVar is not a subtype of the bound">T1</warning>](): ...
               def foo4[T1: (int, bool), T2: str = <warning descr="Default type of TypeVar is not a subtype of the bound">T1</warning>](): ...
               def foo5[T1: (int, bool), T2: (int, str) = <warning descr="Default type of TypeVar must be one of the constraint types">T1</warning>](): ...
               def foo6[T1: (int, str, float), T2: (int, float) = <warning descr="Default type of TypeVar must be one of the constraint types">T1</warning>](): ...
               def foo7[T1: (int, str) = <warning descr="Default type of TypeVar must be one of the constraint types">bool</warning>](): ...
               
               # OK
               def bar1[T1: int = bool](): ...
               def bar2[T1: (int, str) = str](): ...
               def bar3[T1: bool, T2: int = T1](): ...
               def bar4[T1: (int, str), T2: (str, int) = T1](): ...
               def bar5[T1: (int, str), T2: (str, int, float) = T1](): ...
               """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default type matched with object`() {
    doTest("""
                   from typing import TypeVar, Any
                   
                   T = TypeVar('T')
                   T1 = TypeVar('T1', bound=object, default=T)
                   T2 = TypeVar('T2', int, object, default=T)
                   """)
  }

  @TestFor(issues = ["PY-76870"])
  fun `test TypeVar default Any in constraints and bound`() {
    doTest("""
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
               NotOk1 = TypeVar("NotOk1", int, str, default=<warning descr="Default type of TypeVar must be one of the constraint types">Y1</warning>)
               Y2 = TypeVar("Y2", bound=Any)
               NotOk2 = TypeVar("NotOk2", int, str, default=<warning descr="Default type of TypeVar must be one of the constraint types">Y2</warning>)
               Y3 = TypeVar("Y3", bound=str)
               NotOk3 = TypeVar("NotOk3", int, Any, default=<warning descr="Default type of TypeVar must be one of the constraint types">Y3</warning>)
               Y4 = TypeVar("Y4", str, Any)
               NotOk4 = TypeVar("NotOk4", int, Any, default=<warning descr="Default type of TypeVar must be one of the constraint types">Y4</warning>)
               Y5 = TypeVar("Y5", bound=Any)
               NotOk5 = TypeVar("NotOk5", int, str, Any, default=<warning descr="Default type of TypeVar must be one of the constraint types">Y5</warning>)
               """)
  }

  @TestFor(issues = ["PY-76852"])
  fun `test two unpacked unbound tuples`() {
    doTest("""
               from typing import TypeVarTuple, Unpack
               
               t1: <warning descr="Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple">tuple[*tuple[str, ...], *tuple[int, ...]]</warning>
               t2: <warning descr="Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple">tuple[*tuple[str, *tuple[str, ...]], *tuple[int, ...]]</warning>
               t3: <warning descr="Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple">tuple[Unpack[tuple[str, ...]], Unpack[tuple[int, ...]]]</warning>
               t4: <warning descr="Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple">tuple[Unpack[tuple[str, Unpack[tuple[str, ...]]]], Unpack[tuple[int, ...]]]</warning>
               
               # > An unpacked TypeVarTuple counts as an unbounded tuple in the context of this rule
               
               Ts = TypeVarTuple("Ts")
               
               
               def func(t: tuple[*Ts]):
                   t5: tuple[*tuple[str], *Ts]
                   t6: <warning descr="Type argument list can have at most one unpacked TypeVarTuple or unbounded tuple">tuple[*tuple[str, ...], *Ts]</warning>
               """)
  }

  @TestFor(issues = ["PY-76862"])
  fun `test check circular references`() {
    doTest("""
                   from typing import TypeAlias
                   class ClassA:
                       ...
                   
                   type ClassB = str
                   
                   ClassC = int
                   
                   ClassD: TypeAlias = bool
                   
                   circular: <error descr="Circular reference">"circular"</error> = None
                   
                   class Test:
                       ClassA: "ClassA"  # OK
                       ClassB: "ClassB"  # OK
                       ClassC: "ClassC"  # OK
                       ClassD: "ClassD"  # OK
                   
                       ClassE: <error descr="Circular reference">"ClassE"</error>  # E: circular reference
                   
                       ClassG: <error descr="Circular reference">"ClassG"</error> = None  # E: circular reference
                   
                       def foo(self):
                          Test: "Test"
                          ClassA: "ClassA"  # OK
                          ClassB: "ClassB"  # OK
                          ClassC: "ClassC"  # OK
                          str: "str"  # OK
                          def int(self) -> None:
                                  ...
                          x: "int" = 0 # OK
                          var: <error descr="Circular reference">"var"</error> = None  # E: circular reference
                   """)
  }

  fun `test Concatenate not reported as illegal first param`() {
    doTest("""
                   from typing import Callable, Concatenate
                   x: Callable[Concatenate[int, ...], str]
                   """)
  }


  @TestFor(issues = ["PY-76851"])
  fun `test invalid type alias statement`() {
    doTest("""
                   var1 = 1
                   type BadTypeAlias1 = <warning descr="Invalid type annotation">eval(<warning descr="Generics should be specified through square brackets">"".join(<warning descr="Generics should be specified through square brackets">map(chr, [105, 110, 116])</warning>)</warning>)</warning>
                   type BadTypeAlias2 = <warning descr="Invalid type annotation">[int, str]</warning>
                   type BadTypeAlias3 = (<warning descr="Invalid type annotation">(int, str),</warning>)
                   type BadTypeAlias4 = <warning descr="Invalid type annotation">[int for i in <warning descr="Generics should be specified through square brackets">range(1)</warning>]</warning>
                   type BadTypeAlias5 = <warning descr="Invalid type annotation">{"a": "b"}</warning>
                   type BadTypeAlias6 = <warning descr="Invalid type annotation">(lambda: int)()</warning>
                   type BadTypeAlias7 = <warning descr="Invalid type annotation">[int][0]</warning>
                   type BadTypeAlias8 = <warning descr="Invalid type annotation">int if 1 < 3 else str</warning>
                   type BadTypeAlias9 = <warning descr="Invalid type annotation">var1</warning>
                   type BadTypeAlias10 = <warning descr="Invalid type annotation">True</warning>
                   type BadTypeAlias11 = <warning descr="Invalid type annotation">1</warning>
                   type BadTypeAlias12 = <warning descr="Invalid type annotation">list or set</warning>
                   type BadTypeAlias13 = <warning descr="Invalid type annotation">f"{'int'}"</warning>
                   """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test type alias statement scope`() {
    doTest("""
               type B = int
               class C:
                   type D = int
               def func():
                   <warning descr="A 'type' statement can be used only within a module or class scope">type A = int</warning>
               """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test type alias bound match`() {
    doTest("""
               type TypeAlias[S: str] = list[S]
               r: TypeAlias[str] = [""]
               """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test type alias bound mismatch`() {
    doTest("""
               type TypeAlias[S: int] = list[S]
               r: TypeAlias[<warning descr="Expected type 'S ≤: int', got 'str' instead">str</warning>] = [""]
               """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test type alias old-style bound mismatch`() {
    doTest("""
               from typing import TypeAlias, TypeVar
               T = TypeVar("T", bound=str)
               Alias: TypeAlias = list[T]
               x: Alias[<warning descr="Expected type 'T ≤: str', got 'int' instead">int</warning>]
               """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test class variadic type parameters`() {
    doTest("""
               from typing import Callable

               class A[S1, **S2]:
                   t: Callable[S2, S1]
               
               a: A[int, ...]
               """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test class bound mismatch`() {
    doTest("""
               class C[T: str]: ...
               c = C[<warning descr="Expected type 'T ≤: str', got 'int' instead">int</warning>]()
               """)
  }

  @TestFor(issues = ["PY-88277"])
  fun `test class TypeVarTuple bound mismatch`() {
    doTest("""
                   from typing import Unpack
                   
                   class C[*Ts: <error descr="Type variable tuples cannot have constraints or upper bounds">str</error>]: ...
                   c = C[str, str]()
                   c = C[<warning descr="Expected type '*Ts ≤: str', got '*tuple[str, int]' instead">str, int</warning>]()
                   c = C[<warning descr="Expected type '*Ts ≤: str', got '*tuple[int, str]' instead">int, str</warning>]()
                   
                   class D[*Ts: <error descr="Type variable tuples cannot have constraints or upper bounds">Unpack[tuple[str]]</error>]: ...
                   d = D[str]()
                   d = D[<warning descr="Expected type '*Ts ≤: *tuple[str]', got '*tuple[str, str]' instead">str, str</warning>]()
                   d = D[<warning descr="Expected type '*Ts ≤: *tuple[str]', got '*tuple[int, str]' instead">int, str</warning>]()
                   """)
  }

  @TestFor(issues = ["PY-88277"])
  fun `test class ParamSpec bound mismatch`() {
    doTest("""
                   class C[**P: <error descr="Parameter specifications cannot have constraints or upper bounds">[str]</error>]: ...
                   c = C[<warning descr="Expected type '**P ≤: [str]', got '[int]' instead">int</warning>]()
                   """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test type alias variadic type parameters`() {
    doTest("""
               from typing import Callable

               type TypeAlias[S1, **S2] = Callable[S2, S1]
               type TypeAlias2 = TypeAlias[int, ...]
               """)
  }


  @TestFor(issues = ["PY-76851"])
  fun `test simple recursive type alias statement`() {
    doTest("""
                   type TypeAlias = <warning descr="Invalid type annotation">TypeAlias</warning>
                   """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test recursive type alias statement in union`() {
    doTest("""
                   type TypeAlias = int | str | <warning descr="Circular reference">TypeAlias</warning>
                   type TypeAlias2 = int | str
                   """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test union recursive type alias statement`() {
    doTest("""
                   type TypeAlias = <warning descr="Circular reference">TypeAlias</warning> | int
                   """)
  }


  @TestFor(issues = ["PY-76851"])
  fun `test deep recursive type alias statement`() {
    doTest("""
                   type TypeAlias1 = <warning descr="Invalid type annotation">TypeAlias2</warning>
                   type TypeAlias2 = <warning descr="Invalid type annotation">TypeAlias3</warning>
                   type TypeAlias3 = <warning descr="Invalid type annotation">TypeAlias1</warning>
                   """)
  }

  @TestFor(issues = ["PY-76851"])
  fun `test correct recursive type alias statement`() {
    doTest("""
                   type TypeAlias1 = list[TypeAlias1]
                   """)
  }

  @TestFor(issues = ["PY-82979"])
  fun testImplicitTypeAliasUsingLiteralMultiFile() {
    doMultiFileTest()
  }

  @TestFor(issues = ["PY-81028"])
  fun testImplicitTypeAliasUsingAnnotatedMultiFile() {
    doMultiFileTest()
  }

  @TestFor(issues = ["PY-83583"])
  fun `test implicit type alias using Annotated with new-style union`() {
    doTest("""
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
  }

  @TestFor(issues = ["PY-83699"])
  fun `test implicit type alias using Callable with new-style union`() {
    doTest("""
                   from typing import Awaitable, Any, Callable
                   
                   AsyncFunc = Callable[[int], Awaitable[Any | None]]
                   
                   f: AsyncFunc
                   """)
  }

  @TestFor(issues = ["PY-83700"])
  fun testImplicitTypeAliasAtClassLevelMultiFile() {
    doMultiFileTest()
  }

  @TestFor(issues = ["PY-81926"])
  fun `test union with class overriding dunder OR`() {
    doTest("""
                   from typing import Self
                   
                   class Cls:
                       def __or__(self, other: Self) -> Self:
                           return self
                   
                   def foo(arg: Cls | None) -> None:
                       print(arg)
                   """)
  }

  @TestFor(issues = ["PY-81439"])
  fun `test implicit type alias using Literal`() {
    doTest("""
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
  }

  fun testSelfImportedFromNonExcludedTypingExtensionsMultiFile() {
    doMultiFileTest()
  }

  @TestFor(issues = ["PY-76832"])
  fun `test type Self as type arg`() {
    doTest("""
                   from typing import TypeAlias, Self
                   TupleSelf: TypeAlias = tuple[<warning descr="Cannot use 'Self' outside class">Self</warning>]  # E
                   class A[T]: ...
                   a = A[<warning descr="Cannot use 'Self' outside class">Self</warning>]()  # E
                   class B:
                      def __init__(self):
                          self.l: List[Self] = []  # OK
                   """)
  }

  @TestFor(issues = ["PY-76832"])
  fun `test type Self in base class type args`() {
    doTest("""
                   from typing import Self
                   
                   class Bar[T]: ...
                   class Baz(Bar[<warning descr="Cannot use 'Self' in this context">Self</warning>]): ... # E
                   """)
  }

  @TestFor(issues = ["PY-76832"])
  fun `test type Self in metaclass`() {
    doTest("""
                   from typing import Self, Any
                   
                   class MyMetaclass(type):
                       def __new__(cls, *args: Any) -> <warning descr="Type 'Self' cannot be used in a metaclass">Self</warning>:  # E
                           ...
                   
                       def __mul__(cls, count: int) -> list[<warning descr="Type 'Self' cannot be used in a metaclass">Self</warning>]:  # E
                           ...
                   """)
  }

  fun `test subscription parentheses flattening`() {
    generateVariableTypeAssertions(arrayOf(
      arrayOf("list[((int))]", "list[int]"),
      // TODO: type: list[Any]
      arrayOf("list[((<warning descr=\"Passed type arguments do not match type parameters [_T] of class 'list'\">int, int</warning>))]"),

      arrayOf("tuple[((int, int))]", "tuple[int, int]"),
      arrayOf("tuple[<error descr=\"Invalid type argument\">((int, int))</error>, int]", "tuple[Unknown, int]"),

      arrayOf("set[((int))]", "set[int]"),
      // TODO: type: set[Any]
      arrayOf("set[((<warning descr=\"Passed type arguments do not match type parameters [_T] of class 'set'\">int, int</warning>))]"),

      arrayOf("dict[((int)), (((str)))]", "dict[int, str]"),
      // TODO: type: dict[Any, Any]
      arrayOf("dict[((<warning descr=\"Passed type arguments do not match type parameters [_KT, _VT] of class 'dict'\">int</warning>))]"),

      arrayOf("List[((int))]", "list[int]"),
      // TODO: type: list[Any]
      arrayOf("List[((<warning descr=\"Passed type arguments do not match type parameters [_T] of class 'list'\">int, int</warning>))]"),

      arrayOf("Tuple[((int)), (((str)))]", "tuple[int, str]"),
      arrayOf("Tuple[<error descr=\"Invalid type argument\">((int, int))</error>, int]", "tuple[Unknown, int]"),

      arrayOf("Set[((int))]", "set[int]"),
      // TODO: type: set[Any]
      arrayOf("Set[((<warning descr=\"Passed type arguments do not match type parameters [_T] of class 'set'\">int, int</warning>))]"),

      arrayOf("Dict[((int)), (((str)))]", "dict[int, str]"),
      // TODO: type: dict[Any, Any]
      arrayOf("Dict[((<warning descr=\"Passed type arguments do not match type parameters [_KT, _VT] of class 'dict'\">int</warning>))]"),

      arrayOf("Union[((int, (((str)))))]", "int | str"),
      arrayOf("Union[<error descr=\"Invalid type argument\">((int, int))</error>, <error descr=\"Invalid type argument\">(int, int)</error>]",
              "Unknown"),

      arrayOf("Optional[((int))]", "int | None"),
      arrayOf("Optional[((<error descr=\"'Optional' must have exactly one argument\">int, int</error>))]", "Unknown"),

      arrayOf("tuple[((int)), ((...))]", "tuple[int, ...]"),
      arrayOf("tuple[((<error descr=\"'...' is allowed only as the second of two arguments\">...</error>)), ((int))]", "Unknown"),
      arrayOf("tuple[<error descr=\"Invalid type argument\">(int,)</error>, ...]", "tuple[Unknown, ...]"),

      arrayOf("C[(((int)))]", "C[int]"),
      arrayOf("C2[(((int), (str)))]", "C2[int, str]"),
    ))
  }

  fun `test subscription empty parentheses`() {
    generateVariableTypeAssertions(arrayOf(
      arrayOf("tuple[()]", "tuple[()]"),
      arrayOf("tuple[int, <error descr=\"Empty tuple is allowed only as a sole argument\">()</error>]", "Unknown"),

      arrayOf("tuple[<error descr=\"Empty tuple is allowed only as a sole argument\">()</error>, int]", "Unknown"),
      arrayOf("tuple[<error descr=\"Empty tuple is allowed only as a sole argument\">()</error>, ...]", "tuple[Unknown, ...]"),
      arrayOf("tuple[<error descr=\"Empty tuple is allowed only as a sole argument\">()</error>, <error descr=\"Empty tuple is allowed only as a sole argument\">()</error>]",
              "Unknown"),

      arrayOf("Tuple[()]", "tuple[()]"),
      arrayOf("Tuple[int, <error descr=\"Empty tuple is allowed only as a sole argument\">()</error>]", "Unknown"),

      // TODO: type: list[Any]
      arrayOf("list[<warning descr=\"Passed type arguments do not match type parameters [_T] of class 'list'\">()</warning>]"),
      // TODO: type: set[Any]
      arrayOf("set[<warning descr=\"Passed type arguments do not match type parameters [_T] of class 'set'\">()</warning>]"),
      // TODO: type: dict[Any, Any]
      arrayOf("dict[<error descr=\"Invalid type argument\">()</error>, <error descr=\"Invalid type argument\">()</error>]"),

      // TODO: type: list[Any]
      arrayOf("List[<warning descr=\"Passed type arguments do not match type parameters [_T] of class 'list'\">()</warning>]"),
      // TODO: type: set[Any]
      arrayOf("Set[<warning descr=\"Passed type arguments do not match type parameters [_T] of class 'set'\">()</warning>]"),
      // TODO: type: dict[Any, Any]
      arrayOf("Dict[<error descr=\"Invalid type argument\">()</error>, <error descr=\"Invalid type argument\">()</error>]"),

      arrayOf("Union[()]", "Never"),

      arrayOf("Optional[<error descr=\"'Optional' must have exactly one argument\">()</error>]", "Unknown"),

      // TODO: type: C[Any]
      arrayOf("C[<warning descr=\"Passed type arguments do not match type parameters [T] of class 'C'\">()</warning>]"),
    ))
  }

  fun `test subscription type form`() {
    generateVariableTypeAssertions(arrayOf(
      arrayOf("list[((int,))]", "list[int]"),

      arrayOf("tuple[((int,))]", "tuple[int]"),
      arrayOf("tuple[<error descr=\"Invalid type argument\">(int,)</error>, int]", "tuple[Unknown, int]"),
      arrayOf("tuple[<error descr=\"Invalid type argument\">(int, str)</error>, <error descr=\"Invalid type argument\">(int, str)</error>]",
              "tuple[Unknown, Unknown]"),

      arrayOf("set[((int,))]", "set[int]"),

      arrayOf("dict[<error descr=\"Invalid type argument\">((int,))</error>, str]", "dict[Unknown, str]"),
      arrayOf("dict[int, <error descr=\"Invalid type argument\">(int, str)</error>]", "dict[int, Unknown]"),
      // TODO: type: dict[Any, Any]
      arrayOf("dict[((<warning descr=\"Passed type arguments do not match type parameters [_KT, _VT] of class 'dict'\">[int]</warning>))]"),

      arrayOf("List[((int,))]", "list[int]"),

      arrayOf("Tuple[((int,))]", "tuple[int]"),
      arrayOf("Tuple[<error descr=\"Invalid type argument\">(int,)</error>, int]", "tuple[Unknown, int]"),

      arrayOf("Set[((int,))]", "set[int]"),

      arrayOf("Dict[<error descr=\"Invalid type argument\">((int,))</error>, str]", "dict[Unknown, str]"),
      arrayOf("Dict[int, <error descr=\"Invalid type argument\">(int, str)</error>]", "dict[int, Unknown]"),
      arrayOf("Dict[((<error descr=\"Parameters to generic types must be types\">[int]</error>))]"),  // TODO: type: dict[Any, Any]

      arrayOf("tuple[Tuple[int, str]]", "tuple[tuple[int, str]]"),
      arrayOf("Tuple[tuple[int], ...]", "tuple[tuple[int], ...]"),
      arrayOf("tuple[*Tuple[*tuple[int]]]", "tuple[int]"),
      arrayOf("tuple[int, *Tuple[*Tuple[int, str]], str]", "tuple[int, int, str, str]"),
      arrayOf("tuple[*tuple[int], *Tuple[int]]", "tuple[int, int]"),

      arrayOf("Union[((int, int,))]", "int"),
      arrayOf("Union[<error descr=\"Invalid type argument\">(int, int,)</error>, int]", "int | Unknown"),

      arrayOf("Optional[<error descr=\"'Optional' must have exactly one argument\">int, int</error>]", "Unknown"),
      arrayOf("Optional[(int,)]", "int | None"),

      arrayOf("Callable[<error descr=\"'Callable' first parameter must be a parameter expression\">int</error>, int]", "Unknown"),
      arrayOf("Callable[[int], ((<error descr=\"Parameters to generic types must be types\">[int]</error>))]", "Callable[[int], Unknown]"),

      // TODO: type: list[Any]
      arrayOf("list[((<warning descr=\"Passed type arguments do not match type parameters [_T] of class 'list'\">[int]</warning>))]"),
      arrayOf("List[((<error descr=\"Parameters to generic types must be types\">[int]</error>))]"),

      arrayOf("C[int]", "C[int]"),
      arrayOf("C[int,]", "C[int]"),
      arrayOf("C[((int))]", "C[int]"),
      arrayOf("C[((int)),]", "C[int]"),
      arrayOf("C[((int,))]", "C[int]"),
      arrayOf("C[((((int)),))]", "C[int]"),

      arrayOf("C[(((<warning descr=\"Unbound type variable\">TV</warning>)))]"),
      arrayOf("list[((<warning descr=\"Unbound type variable\">TV</warning>))]"),
      arrayOf("<warning descr=\"'Generic' cannot be used as a type expression\">Generic</warning>[((<warning descr=\"Unbound type variable\">TV</warning>))]"),
      arrayOf("dict[((int)), (((<warning descr=\"Unbound type variable\">TV</warning>)))]"),
      arrayOf("Annotated[((str, dict[str, str]))]"),
    ))
  }

  fun `test subscription ellipsis type form`() {
    generateVariableTypeAssertions(arrayOf(
      arrayOf("tuple[int, ...]", "tuple[int, ...]"),

      arrayOf("tuple[<error descr=\"'...' is allowed only as the second of two arguments\">...</error>, int]", "Unknown"),
      arrayOf("tuple[int, int, <error descr=\"'...' is allowed only as the second of two arguments\">...</error>]", "Unknown"),
      arrayOf("tuple[int, <error descr=\"'...' is allowed only as the second of two arguments\">...</error>, int]", "Unknown"),
      arrayOf("tuple[<error descr=\"'...' is allowed only as the second of two arguments\">...</error>]", "Unknown"),
      arrayOf("tuple[<error descr=\"'...' is allowed only as the second of two arguments\">...</error>, ...]", "Unknown"),

      arrayOf("set[<error descr=\"Invalid type argument\">...</error>]", "set[Unknown]"),
      arrayOf("list[<error descr=\"Invalid type argument\">...</error>]", "list[Unknown]"),
      arrayOf("dict[<error descr=\"Invalid type argument\">...</error>]"),  // TODO: type: "dict[Any, Any]"

      arrayOf("Union[int, <error descr=\"Invalid type argument\">...</error>]", "int | Unknown"),
      arrayOf("Optional[<error descr=\"Invalid type argument\">...</error>]", "Unknown"),

      arrayOf("tuple[*tuple[str], <error descr=\"'...' cannot be used with an unpacked 'TypeVarTuple' or tuple\">...</error>]", "Unknown"),
      arrayOf("tuple[*tuple[str, ...], <error descr=\"'...' cannot be used with an unpacked 'TypeVarTuple' or tuple\">...</error>]",
              "Unknown"),

      arrayOf("Set[<error descr=\"Invalid type argument\">...</error>]", "set[Unknown]"),
      arrayOf("List[<error descr=\"Invalid type argument\">...</error>]", "list[Unknown]"),
      arrayOf("Dict[<error descr=\"Invalid type argument\">...</error>]"),  // TODO: type: "dict[Any, Any]"

      arrayOf("Tuple[int, ...]", "tuple[int, ...]"),
      arrayOf("Tuple[<error descr=\"'...' is allowed only as the second of two arguments\">...</error>]", "Unknown"),

      arrayOf("C[<error descr=\"Invalid type argument\">...</error>]", "C[Unknown]"),
    ))
  }

  @TestFor(issues = ["PY-84289"])
  fun testExponentialAnalysisTimeWhenMapLookupKeyEqualsVariableName() {
    val before = System.currentTimeMillis()
    doMultiFileTest("main.py")
    val after = System.currentTimeMillis()
    val diff = after - before
    // junit3 doesn't support timeouts out of the box
    if (diff > 5000) {
      fail("Took too long to analyze main.py: $diff ms")
    }
  }

  @TestFor(issues = ["PY-84570"])
  fun `test list literal is not considered type alias`() {
    doTest("""
                   from enum import Enum
                   from typing import TypeAlias
                   
                   class Direction(Enum):
                       NORTH = "N"
                       SOUTH = "S"
                       EAST = "E"
                       WEST = "W"
                   
                   CARTESIAN = [Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST]
                   print(CARTESIAN[0])
                   
                   type Alias = <warning descr="Invalid type annotation">[int, str]</warning>
                   myAlias: TypeAlias = <warning descr="Assigned value of type alias must be a correct type">[int, str]</warning>
                   """)
  }

  @TestFor(issues = ["PY-85120"])
  fun `test target expression with annotation not considered type alias`() {
    doTest("""
                   a: list[int] | None = None  # Not a type alias
                   
                   if a:
                       _ = a[1]  # No error expected
                   """)
  }

  @TestFor(issues = ["PY-86310"])
  fun `test target expression with reassignment not processed as implicit type alias`() {
    doTest("""
                   b = []
                   a = b
                   _ = a[0] # No error expected
                   """)
  }

  @TestFor(issues = ["PY-86223"])
  fun `test generic type with quoted type parameter in type hint`() {
    doTest("""
                   from typing import assert_type
                   
                   
                   def foo[T](x: list["T"]):
                       assert_type(x, list[T])
                       assert_type(x, list["T"])
                   """)
  }

  @TestFor(issues = ["PY-76895"])
  fun `test invalid expression inside bound`() {
    doTest(
      """
        var = 1
        class ClassA[T: (<warning descr="Invalid type annotation">3</warning>, bytes)]: ...
        class ClassB[T: (int, <warning descr="Invalid type annotation">[1, 2, 3]</warning>)]: ...
        class ClassC[T: (int, <warning descr="Invalid type annotation">var</warning>)]: ...
        class ClassC[T: (int, <warning descr="Invalid type annotation">lambda x: x</warning>)]: ...
        class ClassD[T: (int, <warning descr="Invalid type annotation">ClassA[bytes]()</warning>)]: ...
        
        class ClassA[T: (<warning descr="Invalid type annotation">3</warning>, bytes)]: ...
        class ClassB[T: (int, <warning descr="Invalid type annotation">[1, 2, 3]</warning>)]: ...
        class ClassC[T: (int, <warning descr="Invalid type annotation">var</warning>)]: ...
        class ClassC[T: (int, <warning descr="Invalid type annotation">lambda x: x</warning>)]: ...
        class ClassD[T: (int, <warning descr="Invalid type annotation">ClassA[bytes]()</warning>)]: ...
        class ClassD[T: <warning descr="Invalid type annotation">[int]</warning>]: ...
        """)
  }

  @TestFor(issues = ["PY-89092"])
  fun `test ParamSpec in bound`() {
    doTest(
      """
        from collections.abc import Callable
        
        class A[**P]: ...
        class B[T: Callable[[], None] = Callable[[], None]]: ...
        class C[T: A[[]] = A[[]]]: ...
        """)
  }

  @TestFor(issues = ["PY-76895"])
  fun `test invalid expression in default`() {
    doTest(
      """
        var = 1
        class ClassA[T: (<warning descr="Invalid type annotation">3</warning>, bytes)]: ...
        class ClassB[T: (int, <warning descr="Invalid type annotation">[1, 2, 3]</warning>)]: ...
        class ClassC[T: (int, <warning descr="Invalid type annotation">var</warning>)]: ...
        class ClassC[T: (int, <warning descr="Invalid type annotation">lambda x: x</warning>)]: ...
        class ClassD[T: (int, <warning descr="Invalid type annotation">ClassA[bytes]()</warning>)]: ...
        class ClassE[T: <warning descr="Invalid type annotation">3</warning>]: ...
        """)
  }

  @TestFor(issues = ["PY-87564"])
  fun `test type variable bound with module qualifier`() {
    myFixture.configureByText("mod.py", "class MyClass: pass")
    doTest(
      """
        import mod
        
        class A[T: mod.MyClass]: ...
        """)
  }

  private fun generateVariableTypeAssertions(cases: Array<Array<String?>>) {
    val body = StringBuilder()

    for (i in cases.indices) {
      val c = cases[i]

      val annotationText = c[0]
      val variableName = "variable_" + (i + 1)

      body.append(variableName).append(": ").append(annotationText).append("\n")

      if (c.size > 1) {
        val expectedTypeText = c[1]
        body.append("assert_type(").append(variableName).append(", ").append(expectedTypeText).append(")\n")
      }
    }

    myFixture.enableInspections(PyAssertTypeInspection::class.java)

    doTest(
      ("""
         from typing import assert_type, TypeVar, Generic, Any, Never, List, Set, Dict, Tuple, Union, Optional, Callable, Annotated

         TV = TypeVar("TV")

         class C[T]: ...
         class C2[T1, T2]: ...
         """.trimIndent() + "\n" + body).trim()
    )
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec components swapped`() {
    doTest("""
                   def mixed_up[**P](*args: <warning descr="'P.kwargs' can only be used to annotate '**kwargs' parameters">P.kwargs</warning>, **kwargs: <warning descr="'P.args' can only be used to annotate '*args' parameters">P.args</warning>) -> None:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component on regular param`() {
    doTest("""
                   def misplaced[**P](x: <warning descr="ParamSpec component can only be used to annotate '*args' or '**kwargs' parameters">P.args</warning>) -> None:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component same for both`() {
    doTest("""
                   def bad[**P](*args: P.args, **kwargs: <warning descr="'P.args' can only be used to annotate '*args' parameters">P.args</warning>) -> None:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec components kwargs with illegal annotation`() {
    doTest("""
                   from typing import Any
                   def bad[**P](*args: <warning descr="'P.args' and 'P.kwargs' must both be present in the same function signature">P.args</warning>, **kwargs: Any) -> None:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component not in scope`() {
    doTest("""
                   from typing import ParamSpec
                   P = ParamSpec("P")
                   def out_of_scope(*args: <warning descr="ParamSpec 'P' must be a type parameter of the enclosing callable or class">P</warning>.args, **kwargs: <warning descr="ParamSpec 'P' must be a type parameter of the enclosing callable or class">P</warning>.kwargs) -> None:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component as variable annotation`() {
    doTest("""
                   def foo[**P]() -> None:
                       stored_args: <warning descr="ParamSpec component can only be used to annotate '*args' or '**kwargs' parameters">P.args</warning>
                       stored_kwargs: <warning descr="ParamSpec component can only be used to annotate '*args' or '**kwargs' parameters">P.kwargs</warning>
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component unpaired args`() {
    doTest("""
                   def just_args[**P](*args: <warning descr="'P.args' and 'P.kwargs' must both be present in the same function signature">P.args</warning>) -> None:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component unpaired kwargs`() {
    doTest("""
                   def just_kwargs[**P](**kwargs: <warning descr="'P.args' and 'P.kwargs' must both be present in the same function signature">P.kwargs</warning>) -> None:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component keyword-only between`() {
    doTest("""
                   def bar[**P](*args: P.args, <warning descr="No parameters allowed between 'P.args' and 'P.kwargs'">s: str</warning>, **kwargs: P.kwargs) -> None:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component in scope via Generic class`() {
    doTest("""
                   from typing import ParamSpec, Generic
                   P = ParamSpec("P")
                   class Wrapper(Generic[P]):
                       def call(self, *args: P.args, **kwargs: P.kwargs) -> None:
                           pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component in scope via Protocol class`() {
    doTest("""
                   from typing import ParamSpec, Protocol
                   P = ParamSpec("P")
                   class Proto(Protocol[P]):
                       def __call__(self, *args: P.args, **kwargs: P.kwargs) -> None: ...
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component in scope new-style generic class`() {
    doTest("""
                   class Wrapper[**P]:
                       def call(self, *args: P.args, **kwargs: P.kwargs) -> None:
                           pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec component not in scope in class`() {
    doTest("""
                   from typing import ParamSpec
                   P = ParamSpec("P")
                   class NoParamSpec:
                       def call(self, *args: <warning descr="ParamSpec 'P' must be a type parameter of the enclosing callable or class">P</warning>.args, **kwargs: <warning descr="ParamSpec 'P' must be a type parameter of the enclosing callable or class">P</warning>.kwargs) -> None:
                           pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test ParamSpec components valid usage`() {
    doTest("""
                   from typing import Callable, ParamSpec
                   P = ParamSpec("P")

                   def valid1[**P](*args: P.args, **kwargs: P.kwargs) -> None:
                       pass

                   def valid2[**P](s: str, *args: P.args, **kwargs: P.kwargs) -> None:
                       pass

                   def twice(f: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> int:
                       return f(*args, **kwargs)
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test after ParamSpec args kwargs param without annotation`() {
    doTest("""
                   from typing import ParamSpec, TypeVar, Callable
                   P = ParamSpec("P")
                   T = TypeVar("T")
                   
                   def invoke(fn: Callable[P, T], *args: <warning descr="'P.args' and 'P.kwargs' must both be present in the same function signature">P.args</warning>, **kwargs) -> T:
                       pass
                   """)
  }

  @TestFor(issues = ["PY-76850"])
  fun `test illegal ParamSpec usage for kwargs`() {
    doTest("""
                   from typing import ParamSpec, TypeVar, Callable
                   P = ParamSpec("P")
                   def invoke(**kwargs: <warning descr="'P.args' and 'P.kwargs' must both be present in the same function signature"><warning descr="ParamSpec 'P' must be a type parameter of the enclosing callable or class">P</warning>.kwargs</warning>) -> None:
                       pass
                   """)
  }

  fun `test explicit tuple in Literal`() {
    doTest(
      """
        from typing import Literal
        
        _: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">(1, "a")</warning>]
        """
    )
  }

  private fun doTest(@Language("Python") text: String) = doTestByText(text.trimIndent())

  override fun getInspectionClass(): Class<out PyInspection> = PyTypeHintsInspection::class.java

  companion object {
    private const val TRIPLE_QUOTE = "\"\"\""
  }
}
