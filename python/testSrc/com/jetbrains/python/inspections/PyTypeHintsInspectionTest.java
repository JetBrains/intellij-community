// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyTypeHintsInspectionTest extends PyInspectionTestCase {

  // PY-28243
  public void testTypeVarAndTargetName() {
    doTestByText("""
                   from typing import TypeVar

                   T0 = TypeVar('T0')
                   T1 = TypeVar(<warning descr="The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned">'T2'</warning>)""");
  }

  // PY-28243
  public void testTypeVarPlacement() {
    doTestByText("""
                   from typing import List, TypeVar

                   T0 = TypeVar('T0')
                   a: List[T0]
                   b: List[<warning descr="A 'TypeVar()' expression must always directly be assigned to a variable">TypeVar('T1')</warning>]""");
  }

  // PY-28243
  public void testTypeVarRedefinition() {
    doTestByText("""
                   from typing import TypeVar

                   T0 = TypeVar('T0')
                   print(T0)
                   <warning descr="Type variables must not be redefined">T0</warning> = TypeVar('T0')""");
  }

  // PY-28124
  public void testTypeVarBivariant() {
    doTestByText("""
                   from typing import TypeVar

                   T1 = <error descr="Bivariant type variables are not supported">TypeVar('T1', contravariant=True, covariant=True)</error>
                   true = True
                   T2 = <error descr="Bivariant type variables are not supported">TypeVar('T2', contravariant=true, covariant=true)</error>""");
  }

  // PY-28124
  public void testTypeVarConstraintsAndBound() {
    doTestByText("""
                   from typing import TypeVar

                   T2 = <error descr="Constraints cannot be combined with bound=...">TypeVar('T2', int, str, bound=str)</error>""");
  }

  // PY-28124
  public void testTypeVarConstraintsNumber() {
    doTestByText("""
                   from typing import TypeVar

                   T1 = <error descr="A single constraint is not allowed">TypeVar('T1', int)</error>
                   T2 = TypeVar('T2', int, str)""");
  }

  // PY-28124
  public void testTypeVarNameAsLiteral() {
    doTestByText("""
                   from typing import TypeVar

                   name = 'T0'
                   T0 = TypeVar(<warning descr="'TypeVar()' expects a string literal as first argument">name</warning>)
                   T1 = TypeVar('T1')""");
  }

  // PY-28243
  public void testTypeVarParameterizedConstraints() {
    doTestByText("""
                   from typing import TypeVar, List

                   T1 = TypeVar('T1', int, str)

                   T2 = TypeVar('T2', int, <warning descr="Constraints cannot be parametrized by type variables">List[T1]</warning>)
                   T3 = TypeVar('T3', bound=<warning descr="Constraints cannot be parametrized by type variables">List[T1]</warning>)

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
                   T55 = TypeVar('T55', bound=my_list_int)""");
  }

  // PY-28227
  public void testPlainGenericInheritance() {
    doTestByText("""
                   from typing import Generic

                   class A(<error descr="Cannot inherit from plain 'Generic'">Generic</error>):
                       pass

                   B = Generic
                   class C(<error descr="Cannot inherit from plain 'Generic'">B</error>):
                       pass""");
  }

  // PY-28227
  public void testGenericParametersTypes() {
    doTestByText("""
                   from typing import Generic, TypeVar

                   class A1(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">0</error>]):
                       pass

                   class B1(Generic[<error descr="Parameters to 'Generic[...]' must all be type variables">int</error>]):
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
                   class C3(Generic[my_t]):
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
                       pass""");
  }

  // PY-28227
  public void testGenericParametersDuplication() {
    doTestByText("""
                   from typing import Generic, TypeVar

                   T = TypeVar('T')

                   class C(Generic[T, <error descr="Parameters to 'Generic[...]' must all be unique">T</error>]):
                       pass

                   B = Generic
                   class A(B[T, <error descr="Parameters to 'Generic[...]' must all be unique">T</error>]):
                       pass

                   T1 = T
                   class D(Generic[T1, <error descr="Parameters to 'Generic[...]' must all be unique">T</error>]):
                       pass""");
  }

  // PY-28227
  public void testGenericDuplication() {
    doTestByText("""
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
                       pass""");
  }

  // PY-28227
  public void testGenericCompleteness() {
    doTestByText("""
                   from typing import Generic, TypeVar, Iterable

                   T = TypeVar('T')
                   S = TypeVar('S')

                   class C<error descr="Some type variables (S) are not listed in 'Generic[T]'">(Generic[T], Iterable[S])</error>:
                       pass

                   B = Generic
                   D = T
                   class A<error descr="Some type variables (S) are not listed in 'Generic[T]'">(B[D], Iterable[S])</error>:
                       pass

                   class E(Generic[T], Iterable[T]):
                       pass

                   class F(B[D], Iterable[<warning descr="Invalid type argument">D</warning>]):
                       pass
                      \s
                   class G(Iterable[T]):
                       pass""");
  }

  // PY-31147
  public void testGenericCompletenessPartiallySpecialized() {
    doTestByText("""
                   from typing import TypeVar, Generic, Dict

                   T = TypeVar("T")

                   class C(Generic[T], Dict[int, T]):
                       pass""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnAny() {
    doTestByText("""
                   from typing import Any

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Any' cannot be used with instance and class checks">Any</error>)
                   B = Any
                   assert issubclass(A, <error descr="'Any' cannot be used with instance and class checks">B</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnNoReturn() {
    doTestByText("""
                   from typing import NoReturn

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'NoReturn' cannot be used with instance and class checks">NoReturn</error>)
                   B = NoReturn
                   assert issubclass(A, <error descr="'NoReturn' cannot be used with instance and class checks">B</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnTypeVar() {
    doTestByText("""
                   from typing import TypeVar

                   T = TypeVar("T")

                   class A:
                       pass

                   assert isinstance(A(), TypeVar)
                   assert issubclass(A, TypeVar)

                   assert isinstance(A(), <error descr="Type variables cannot be used with instance and class checks">T</error>)
                   assert issubclass(A, <error descr="Type variables cannot be used with instance and class checks">T</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnUnionBefore310() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, () -> {
      doTestByText("""
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
                     assert issubclass(A, <error descr="Python version 3.9 does not allow writing union types as X | Y">int | list[str]</error>)""");
    });
  }

  // PY-44974
  public void testInstanceAndClassChecksOnUnionFromFutureAnnotations() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, () -> {
      doTestByText("""
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
                     assert issubclass(A, <error descr="Python version 3.9 does not allow writing union types as X | Y">int | list[str]</error>)""");
    });
  }

  // PY-44974
  public void testInstanceAndClassChecksOnUnion() {
    doTestByText("""
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
                   """);
  }

  // PY-28249
  public void testInstanceAndClassChecksOnOptionalBefore310() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, () -> {
      doTestByText("""
                     from typing import Optional

                     class A:
                         pass

                     assert isinstance(A(), <error descr="'Optional' cannot be used with instance and class checks">Optional</error>)
                     B = Optional
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">B</error>)

                     assert isinstance(A(), <error descr="'Optional' cannot be used with instance and class checks">Optional[int]</error>)
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">B[int]</error>)
                     C = B[int]
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">C</error>)""");
    });
  }

  // PY-28249
  public void testInstanceAndClassChecksOnOptionalFromFutureAnnotations() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, () -> {
      doTestByText("""
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
                     assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">C</error>)""");
    });
  }

  // PY-28249
  public void testInstanceAndClassChecksOnOptional() {
    doTestByText("""
                   from typing import Optional

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Optional' cannot be used with instance and class checks">Optional</error>)
                   B = Optional
                   assert issubclass(A, <error descr="'Optional' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), Optional[int])
                   assert issubclass(A, B[int])
                   C = B[int]
                   assert issubclass(A, C)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnClassVar() {
    doTestByText("""
                   from typing import ClassVar

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'ClassVar' cannot be used with instance and class checks">ClassVar</error>)
                   B = ClassVar
                   assert issubclass(A, <error descr="'ClassVar' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), <error descr="'ClassVar' cannot be used with instance and class checks">ClassVar[int]</error>)
                   assert issubclass(A, <error descr="'ClassVar' cannot be used with instance and class checks">B[int]</error>)
                   C = B[int]
                   assert issubclass(A, <error descr="'ClassVar' cannot be used with instance and class checks">C</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnGeneric() {
    doTestByText("""
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
                   assert issubclass(A, <error descr="'Generic' cannot be used with instance and class checks">C</error>)""");
  }

  // PY-34945
  public void testInstanceAndClassChecksOnFinal() {
    doTestByText("""
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
                   assert issubclass(A, <error descr="'Final' cannot be used with instance and class checks">C</error>)""");
  }

  // PY-35235
  public void testInstanceAndClassChecksOnLiteral() {
    doTestByText("""
                   from typing_extensions import Literal

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Literal' cannot be used with instance and class checks">Literal</error>)
                   B = Literal
                   assert issubclass(A, <error descr="'Literal' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), <error descr="'Literal' cannot be used with instance and class checks">Literal[1]</error>)
                   assert issubclass(A, <error descr="'Literal' cannot be used with instance and class checks">B[1]</error>)
                   C = B[1]
                   assert issubclass(A, <error descr="'Literal' cannot be used with instance and class checks">C</error>)""");
  }

  // PY-42334
  public void testInstanceAndClassChecksOnTypeAlias() {
    doTestByText("""
                   from typing import TypeAlias

                   class A:
                       pass
                      \s
                   assert isinstance(A(), <error descr="'TypeAlias' cannot be used with instance and class checks">TypeAlias</error>)
                   assert issubclass(A, <error descr="'TypeAlias' cannot be used with instance and class checks">TypeAlias</error>)
                   B = TypeAlias
                   assert isinstance(A(), <error descr="'TypeAlias' cannot be used with instance and class checks">B</error>)
                   assert issubclass(A, <error descr="'TypeAlias' cannot be used with instance and class checks">B</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnGenericInheritor() {
    doTestByText("""
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
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnTuple() {
    doTestByText("""
                   from typing import Tuple

                   class A:
                       pass

                   assert isinstance(A(), Tuple)
                   B = Tuple
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">Tuple[int, str]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[int, str]</error>)
                   C = B[int, str]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnType() {
    doTestByText("""
                   from typing import Type

                   class A:
                       pass

                   assert isinstance(A(), Type)
                   B = Type
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">Type[int]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[int]</error>)
                   C = B[int]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnCallable() {
    doTestByText("""
                   from typing import Callable

                   class A:
                       pass

                   assert isinstance(A(), Callable)
                   B = Callable
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">Callable[..., str]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[..., str]</error>)
                   C = B[..., str]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnProtocol() {
    doTestByText("""
                   from typing import Protocol, TypeVar

                   class A:
                       pass
                      \s
                   T = TypeVar("T")

                   assert isinstance(A(), Protocol)
                   B = Protocol
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">Protocol[T]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[T]</error>)
                   C = B[T]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnUserClass() {
    doTestByText("""
                   from typing import Generic, TypeVar

                   class A:
                       pass

                   T = TypeVar("T")   \s

                   class D(Generic[T]):
                       pass

                   assert isinstance(A(), D)
                   B = D
                   assert issubclass(A, B)

                   assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">D[int]</error>)
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B[int]</error>)
                   C = B[int]
                   assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">C</error>)""");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnUnknown() {
    doTestByText("""
                   from m1 import D

                   class A:
                       pass

                   assert isinstance(A(), D)
                   B = D
                   assert issubclass(A, B)

                   assert isinstance(A(), D[int])
                   assert issubclass(A, B[int])
                   C = B[int]
                   assert issubclass(A, C)""");
  }

  // PY-31788
  public void testInstanceAndClassChecksOnGenericParameter() {
    doTestByText("""
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
                       assert issubclass(A, <error descr="Type variables cannot be used with instance and class checks">p3</error>)""");
  }

  public void testTypedDictWithInstanceAndClassChecks() {
    doTestByText(
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
    );
  }

  public void testTypedDictAsTypeVarBound() {
    doTestByText(
      """
        from typing import TypedDict, TypeVar
        
        class Movie(TypedDict):
            name: str
            year: int

        T = TypeVar("T", bound=<warning descr="TypedDict is not allowed as a bound for a TypeVar">TypedDict</warning>)
        U = TypeVar("U", bound=Movie)
        """
    );
  }

  // PY-16853
  public void testParenthesesAndTyping() {
    doTestByText("""
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
                      \s
                   v1 = <error descr="Generics should be specified through square brackets">Union(int, str)</error>
                   v2 = None  # type: <error descr="Generics should be specified through square brackets">Union(int, str)</error>

                   U = Union
                   def i(j: <error descr="Generics should be specified through square brackets">U(int, str)</error>):
                       pass
                      \s
                   v3 = <error descr="Generics should be specified through square brackets">U(int, str)</error>

                   with foo() as bar:  # type: <error descr="Generics should be specified through square brackets">Union(int,str)</error>
                       pass
                      \s
                   for x in []:  # type: <error descr="Generics should be specified through square brackets">Union(int,str)</error>
                       pass
                      \s
                   A1: TypeAlias = <error descr="Generics should be specified through square brackets">Union(int, str)</error>
                   A2: TypeAlias = '<error descr="Generics should be specified through square brackets">Union(int, str)</error>'
                   A3 = <error descr="Generics should be specified through square brackets">Union(int, str)</error>  # type: TypeAlias
                   A3 = '<error descr="Generics should be specified through square brackets">Union(int, str)</error>'  # type: TypeAlias""");
  }

  // PY-16853
  public void testParenthesesAndCustom() {
    doTestByText("""
                   from typing import Generic, TypeVar, TypeAlias

                   T = TypeVar("T")

                   class A(Generic[T]):
                       def __init__(self, v):
                           pass

                   def a(b: <warning descr="Generics should be specified through square brackets">A(int)</warning>):
                       pass

                   def c(d):
                       # type: (<warning descr="Generics should be specified through square brackets">A(int)</warning>) -> None
                       pass

                   def e(f: <warning descr="Generics should be specified through square brackets">A()</warning>):
                       pass

                   def g(h):
                       # type: (<warning descr="Generics should be specified through square brackets">A()</warning>) -> None
                       pass
                      \s
                   v1 = A(int)
                   v2 = None  # type: <warning descr="Generics should be specified through square brackets">A(int)</warning>

                   U = A
                   def i(j: <warning descr="Generics should be specified through square brackets">U(int)</warning>):
                       pass
                      \s
                   v3 = None  # type: <warning descr="Generics should be specified through square brackets">U(int)</warning>

                   A1: TypeAlias = <warning descr="Generics should be specified through square brackets">A(int)</warning>
                   A2: TypeAlias = '<warning descr="Generics should be specified through square brackets">A(int)</warning>'
                   A3 = <warning descr="Generics should be specified through square brackets">A(int)</warning>  # type: TypeAlias
                   A4 = '<warning descr="Generics should be specified through square brackets">A(int)</warning>'  # type: TypeAlias""");
  }

  // PY-20530
  public void testCallableParameters() {
    doTestByText("""
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
                   A4 = 'Callable[<error descr="'Callable' must be used as 'Callable[[arg, ...], result]'">int</error>]'  # type: TypeAlias""");
  }

  // PY-20530
  public void testSelf() {
    doTestByText("""
                   class A:
                       def method(self, i: int):
                           v1: <error descr="Invalid type 'self'">self</error>.B
                           v2 = None  # type: <error descr="Invalid type 'self'">self</error>.B
                           print(self.B)

                       class B:
                           pass

                   class self:
                       pass

                   v: self""");
  }

  // PY-20530
  public void testTupleUnpacking() {
    doTestByText("""
                   a1 = undefined()  # type: int

                   b1, (c1, d1) = undefined()  # type: int, (int, str)
                   e1, (f1, g1), h1 = undefined()  # type: int, (str, int), str

                   b2, (c2, d2) = undefined()  # type: <warning descr="Type comment cannot be matched with unpacked variables">int, (int)</warning>
                   e2, (f2, g2), h2 = undefined()  # type: <warning descr="Type comment cannot be matched with unpacked variables">int, (str), str</warning>""");
  }

  // PY-20530
  public void testAnnotationAndTypeComment() {
    doTestByText(
      """
        a<warning descr="Types specified both in a type comment and annotation">: int</warning> = None  <warning descr="Types specified both in a type comment and annotation"># type: int</warning>

        def foo(a<warning descr="Types specified both in a type comment and annotation">: int</warning>  <warning descr="Types specified both in a type comment and annotation"># type: int</warning>
                ,):
            pass

        def <warning descr="Types specified both in a type comment and annotation">bar</warning>(a: int) -> int:
            <warning descr="Types specified both in a type comment and annotation"># type: (int) -> int</warning>
            pass
           \s
        def <warning descr="Types specified both in a type comment and annotation">baz1</warning>(a: int):
            <warning descr="Types specified both in a type comment and annotation"># type: (int) -> int</warning>
            pass
           \s
        def <warning descr="Types specified both in a type comment and annotation">baz2</warning>(a) -> int:
            <warning descr="Types specified both in a type comment and annotation"># type: (int) -> int</warning>
            pass"""
    );
  }

  // PY-20530
  public void testValidTypeCommentAndParameters() {
    doTestByText("""
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
                          \s
                       # self is specified
                       def spam12(self):
                           # type: (A) -> None
                           pass

                       def egg12(self, a, b):
                           # type: (A, str, bool) -> None
                           pass
                          \s
                       # self is not specified
                       def spam2(self):
                           # type: () -> None
                           pass

                       def egg2(self, a, b):
                           # type: (str, bool) -> None
                           pass
                          \s
                       # cls is not specified\s
                       @classmethod
                       def spam3(cls):
                           # type: () -> None
                           pass

                       @classmethod
                       def egg3(cls, a, b):
                           # type: (str, bool) -> None
                           pass
                      \s
                       # cls is specified   \s
                       @classmethod
                       def spam41(cls):
                           # type: (Type[Bar]) -> None
                           pass

                       @classmethod
                       def egg41(cls, a, b):
                           # type: (Type[Bar], str, bool) -> None
                           pass
                      \s
                       # cls is specified   \s
                       @classmethod
                       def spam42(cls):
                           # type: (Type[A]) -> None
                           pass

                       @classmethod
                       def egg42(cls, a, b):
                           # type: (Type[A], str, bool) -> None
                           pass
                      \s
                       @staticmethod
                       def spam5():
                           # type: () -> None
                           pass

                       @staticmethod
                       def egg5(a, b):
                           # type: (str, bool) -> None
                           pass
                          \s
                       def baz(self, a, b, c, d):
                           # type: (...) -> None
                           pass""");
  }

  // PY-20530
  public void testInvalidTypeCommentAndParameters() {
    doTestByText("""
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
                          \s
                       # self is not specified
                       def spam2(self):
                           <warning descr="The type of self 'int' is not a supertype of its class 'Bar'"># type: (int) -> None</warning>
                           pass

                       def egg2(self, a, b):
                           <warning descr="The type of self 'int' is not a supertype of its class 'Bar'"># type: (int, str, bool) -> None</warning>
                           pass
                          \s
                       # cls is not specified\s
                       @classmethod
                       def spam3(cls):
                           <warning descr="The type of self 'int' is not a supertype of its class 'Type[Bar]'"># type: (int) -> None</warning>
                           pass

                       @classmethod
                       def egg3(cls, a, b):
                           <warning descr="The type of self 'int' is not a supertype of its class 'Type[Bar]'"># type: (int, str, bool) -> None</warning>
                           pass
                      \s
                       # cls is specified   \s
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
                      \s
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
                           pass""");
  }

  // PY-20530
  public void testTypingMemberParameters() {
    doTestByText(
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
    );
  }

  // PY-32530
  public void testAnnotationAndIgnoreComment() {
    doTestByText("""
                   def foo(a: str):  # type: ignore
                       pass
                   def bar(a: Unknown):  # type: ignore[no-untyped-def, name-defined]
                       pass""");
  }

  public void testAnnotatingNonSelfAttribute() {
    doTestByText("""
                   class A:
                       def method(self, b):
                           <warning descr="Non-self attribute could not be type hinted">b.a</warning>: int = 1

                   class B:
                       pass

                   <warning descr="Non-self attribute could not be type hinted">B.a</warning>: str = "2"

                   def func(a):
                       <warning descr="Non-self attribute could not be type hinted">a.xxx</warning>: str = "2\"""");
  }

  // PY-35235
  public void testLiteral() {
    doTestByText("""
                   from typing_extensions import Literal

                   a: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">1 + 2</warning>]
                   b: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">4j</warning>]
                   c: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">3.14</warning>]
                   d: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">...</warning>]

                   class A:
                       pass

                   e: Literal[Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">A</warning>]]
                   f = Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">A</warning>]
                   g: Literal[<warning descr="'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types">f</warning>]""");
  }

  // PY-35235
  public void testLiteralWithoutArguments() {
    doTestByText("""
                   from typing import Literal
                   a: <warning descr="'Literal' must have at least one parameter">Literal</warning> = 1
                   b = 2  # type: <warning descr="'Literal' must have at least one parameter">Literal</warning>""");
  }

  // PY-35235
  public void testNonPlainStringAsTypingLiteralIndex() {
    doTestByText("from typing import Literal\n" +
                 "a: Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">f\"1\"</warning>] = \"1\"");
  }

  public void testParameterizedBuiltinCollectionsBefore39() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () -> {
      doTestByText("""
                     xs: <warning descr="Builtin 'type' cannot be parameterized directly">type[str]</warning>
                     ys: <warning descr="Builtin 'tuple' cannot be parameterized directly">tuple[int, str]</warning>
                     zs: <warning descr="Builtin 'dict' cannot be parameterized directly">dict[int, str]</warning>""");
    });
  }

  // PY-42418
  public void testParameterizedBuiltinCollections() {
    doTestByText("""
                   xs: type[str]
                   ys: tuple[int, str]
                   zs: dict[int, str]""");
  }

  // PY-41847
  public void testAnnotated() {
    doTestByText("""
                   from typing import Annotated

                   a: Annotated[<warning descr="'Annotated' must be called with at least two arguments">1</warning>]
                   b: Annotated[int, 1]
                   c: Annotated[<warning descr="'Annotated' must be called with at least two arguments">...</warning>]

                   class A:
                       pass

                   d: Annotated[A, '']
                   e: Annotated[<warning descr="'Annotated' must be called with at least two arguments">Annotated[A, True]</warning>]
                   f: Annotated[Annotated[<warning descr="'Annotated' must be called with at least two arguments">A</warning>], '']""");
  }

  // PY-41847
  public void testInstanceAndClassChecksOnAnnotated() {
    doTestByText("""
                   from typing import Annotated

                   class A:
                       pass

                   assert isinstance(A(), <error descr="'Annotated' cannot be used with instance and class checks">Annotated</error>)
                   B = Annotated
                   assert issubclass(A, <error descr="'Annotated' cannot be used with instance and class checks">B</error>)

                   assert isinstance(A(), <error descr="'Annotated' cannot be used with instance and class checks">Annotated[1]</error>)
                   assert issubclass(A, <error descr="'Annotated' cannot be used with instance and class checks">B[1]</error>)
                   C = B[int, 2]
                   assert issubclass(A, <error descr="'Annotated' cannot be used with instance and class checks">C</error>)""");
  }

  // PY-41847
  public void testAnnotatedWithoutArguments() {
    doTestByText("""
                   from typing import Annotated
                   a: <warning descr="'Annotated' must be called with at least two arguments">Annotated</warning> = 1
                   b = 2  # type: Annotated[<warning descr="'Annotated' must be called with at least two arguments">int</warning>]""");
  }

  // PY-42334
  public void testParametrizedTypeAliasInExpression() {
    doTestByText("""
                   from typing import TypeAlias

                   Alias = TypeAlias[<error descr="'TypeAlias' cannot be parameterized">int</error>]""");
  }


  // PY-42334
  public void testParametrizedTypeAliasInAnnotation() {
    doTestByText("""
                   from typing import TypeAlias

                   Alias: <warning descr="'TypeAlias' must be used as standalone type hint">TypeAlias</warning>[int]""");
  }

  // PY-42334
  public void testNonTopLevelTypeAlias() {
    doTestByText("""
                   from typing import TypeAlias

                   Alias: Final[<warning descr="'TypeAlias' must be used as standalone type hint">TypeAlias</warning>] = str""");
  }

  // PY-42334
  public void testNotInitializedTypeAlias() {
    doTestByText("""
                   from typing import TypeAlias

                   <warning descr="Type alias must be immediately initialized">Alias</warning>: TypeAlias""");
  }

  // PY-42334
  public void testNotTopLevelTypeAlias() {
    doTestByText("""
                   from typing import TypeAlias

                   def func():
                       <warning descr="Type alias must be top-level declaration">Alias</warning>: TypeAlias = str""");
  }

  // PY-46602
  public void testNoInspectionTypedDictInPython38() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () -> {
      doTestByText("""
                     from __future__ import annotations

                     def hello(i: dict[str, str]):
                         return i""");
    });
  }

  // PY-50401
  public void testParamSpecNameAsLiteral() {
    doTestByText("""
                   from typing import ParamSpec

                   name = 'T0'
                   T0 = ParamSpec(<warning descr="'ParamSpec()' expects a string literal as first argument">name</warning>)
                   T1 = ParamSpec('T1')""");
  }

  // PY-50401
  public void testParamSpecNameAndTargetNameEquality() {
    doTestByText("""
                   from typing import ParamSpec

                   T0 = ParamSpec(<warning descr="The argument to 'ParamSpec()' must be a string equal to the variable name to which it is assigned">'T1'</warning>)
                   T1 = ParamSpec('T1')""");
  }

  // PY-50930
  public void testNoInspectionInCallableParameterParamSpecFromTypingExpressions() {
    doTestByText("""
                   from typing import Callable, TypeVar
                   from typing_extensions import ParamSpec
                   P = ParamSpec("P")
                   R = TypeVar("R")
                   def foo(it: Callable[P, R]) -> Callable[P, R]:
                       ...""");
  }

  // PY-53104
  public void testInstanceAndClassChecksOnTypingSelf() {
    doTestByText("""
                   from typing import Self


                   class A:
                       pass


                   class B:
                       def foo(self: Self):
                           assert isinstance(A(), <error descr="'Self' cannot be used with instance and class checks">Self</error>)
                           assert issubclass(A, <error descr="'Self' cannot be used with instance and class checks">Self</error>)
                   """);
  }

  // PY-53104
  public void testTypingSelfSubscription() {
    doTestByText("""
                   from typing import Self, Generic, TypeVar

                   T = TypeVar("T")


                   class A(Generic[T]):
                       def foo(self):
                           x: Self[<error descr="'Self' cannot be parameterized">int</error>]
                   """);
  }

  // PY-53104
  public void testTypingSelfAnnotationOutsideClass() {
    doTestByText("""
                   from typing import Self

                   def foo() -> <warning descr="Cannot use 'Self' outside class">Self</warning>:
                       pass
                   """);
  }

  // PY-53104
  public void testTypingSelfAnnotationForVariableOutsideClass() {
    doTestByText("""
                   from typing import Self

                   something: <warning descr="Cannot use 'Self' outside class">Self</warning> | None = None
                   """);
  }

  // PY-53104
  public void testTypingSelfInStaticMethod() {
    doTestByText("""
                   from __future__ import annotations
                   from typing import Self

                   class SomeClass:
                       @staticmethod
                       def foo(bar: <warning descr="Cannot use 'Self' in staticmethod">Self</warning>) -> <warning descr="Cannot use 'Self' in staticmethod">Self</warning>:
                           return bar
                   """);
  }

  // PY-53104
  public void testTypingSelfParameterHasDifferentAnnotation() {
    doTestByText("""
                   from __future__ import annotations
                   from typing import Self

                   class SomeClass:
                       def foo(self: SomeClass, bar: <warning descr="Cannot use 'Self' if 'self' parameter is not 'Self' annotated">Self</warning>) -> <warning descr="Cannot use 'Self' if 'self' parameter is not 'Self' annotated">Self</warning>:
                           return self
                   """);
  }

  // PY-53104
  public void testTypingSelfClsParameterHasDifferentAnnotation() {
    doTestByText("""
                   from __future__ import annotations
                   from typing import Self

                   class SomeClass:
                       @classmethod
                       def foo(cls: SomeClass, bar: <warning descr="Cannot use 'Self' if 'cls' parameter is not 'Self' annotated">Self</warning>) -> <warning descr="Cannot use 'Self' if 'cls' parameter is not 'Self' annotated">Self</warning>:
                           return self
                   """);
  }

  // PY-53104
  public void testTypingSelfInStaticMethodBody() {
    doTestByText("""
                   from typing import Self


                   class C:
                       @staticmethod
                       def m():
                           obj: <warning descr="Cannot use 'Self' in staticmethod">Self</warning> = None""");
  }

  // PY-53104
  public void testTypingSelfInFunctionBodySelfParameterHasDifferentAnnotation() {
    doTestByText("""
                   from typing import Self


                   class C:
                       def m(self: C):
                           obj: <warning descr="Cannot use 'Self' if 'self' parameter is not 'Self' annotated">Self</warning> = None
                   """);
  }

  // PY-62301
  public void testTypingSelfInNewMethod() {
    doTestByText("""
                   from typing import Self
                   
                   class ReturnsSelf:
                       def __new__(cls, value: int) -> Self: ...
                   """);
  }

  // PY-36317
  public void testDictSubscriptionNotReportedAsParametrizedGeneric() {
    doTestByText("""
                   keys_and_types = {
                       'comment': (str, type(None)),
                       'from_budget': (bool, type(None)),
                       'to_member': (int, type(None)),
                       'survey_request': (int, type(None)),
                   }
                                      
                   def type_is_valid(test_key, test_value):
                       return isinstance(test_value, keys_and_types[test_key])
                   """);
  }

  // PY-36317
  public void testTupleSubscriptionNotReportedAsParametrizedGeneric() {
    doTestByText("""
                   tuple_of_types = (str, bool, int)
                   
                   def my_is_instance(value, index: int) -> bool:
                       return isinstance(value, tuple_of_types[index])
                   """);
  }

  // PY-36317
  public void testPlainDictTypeSubscriptionNotReportedAsParametrizedGeneric() {
    doTestByText("""
                   def foo(d: dict, s: dict):
                       for key in s.keys():
                           if not isinstance(d[key], s[key]):
                               raise TypeError
                   """);
  }

  // PY-53105
  public void testNoVariadicGenericErrorInClassDeclaration() {
    doTestByText("""
                   from typing import Generic, TypeVarTuple

                   Shape = TypeVarTuple('Shape')


                   class Array(Generic[*Shape]):
                       ...
                   """);
  }

  // PY-53105
  public void testTypeVarTupleNameAsLiteral() {
    doTestByText("""
                   from typing import TypeVarTuple

                   name = 'Ts'
                   Ts = TypeVarTuple(<warning descr="'TypeVarTuple()' expects a string literal as first argument">name</warning>)
                   Ts1 = TypeVarTuple('Ts1')""");
  }

  // PY-53105
  public void testTypeVarTupleNameAndTargetNameEquality() {
    doTestByText("""
                   from typing import TypeVarTuple

                   Ts = TypeVarTuple(<warning descr="The argument to 'TypeVarTuple()' must be a string equal to the variable name to which it is assigned">'T'</warning>)
                   Ts1 = TypeVarTuple('Ts1')""");
  }

  // PY-70528
  public void testTypeVarTupleFromTypingExtensionsNameAndTargetNameEquality() {
    doTestByText("""
                   from typing_extensions import TypeVarTuple
                   
                   Ts = TypeVarTuple(<warning descr="The argument to 'TypeVarTuple()' must be a string equal to the variable name to which it is assigned">'T'</warning>)
                   Ts1 = TypeVarTuple('Ts1')
                   """);
  }

  // PY-53105
  public void testTypeVarTupleMoreThanOneUnpacking() {
    doTestByText("""
                    from typing import TypeVarTuple
                    from typing import Generic
                    
                    Ts1 = TypeVarTuple("Ts1")
                    Ts2 = TypeVarTuple("Ts2")
                    
                    
                    class Array(Generic[*Ts1, <error descr="Parameters to generic cannot contain more than one unpacking">*Ts2</error>]):
                        ...
                    """);
  }


  public void testTypeIsDoesntMatch() {
    doTestByText("""
                    from typing_extensions import TypeIs
                    
                    def <warning descr="Return type of TypeIs 'float' is not consistent with the type of the first parameter 'int'">foo</warning>(x: int) -> TypeIs[float]:
                      ...
                    
                    def bar(x: float) -> TypeIs[float]:
                      ...
                    
                    class A:
                      def <warning descr="Return type of TypeIs 'float' is not consistent with the type of the first parameter 'int'">f1</warning>(self, x: int) -> TypeIs[float]:
                        ...
                    
                      def f2(self, x: float) -> TypeIs[float]:
                        ...
                    
                      @classmethod
                      def <warning descr="Return type of TypeIs 'float' is not consistent with the type of the first parameter 'int'">f3</warning>(cls, x: int) -> TypeIs[float]:
                        ...
                    
                      @classmethod
                      def f4(cls, x: float) -> TypeIs[float]:
                        ...

                      @staticmethod
                      def <warning descr="Return type of TypeIs 'float' is not consistent with the type of the first parameter 'int'">f5</warning>(x: int) -> TypeIs[float]:
                        ...
                    
                      @staticmethod
                      def f6(x: float) -> TypeIs[float]:
                        ...
                    """);
  }

  public void testTypeIsDoesntMatch2() {
    doTestByText("""
                   from typing_extensions import TypeIs
                   
                   class Base:
                       pass
                   
                   class Derived(Base):
                       pass
                   
                   def <warning descr="Return type of TypeIs 'Base' is not consistent with the type of the first parameter 'Derived'">isInt123</warning>(x: Derived) -> TypeIs[Base]:
                       ...
                   """);
  }

  public void testTypeIsMatch() {
    doTestByText("""
                   from typing_extensions import TypeIs
                   
                   class Base:
                       pass
                   
                   class Derived(Base):
                       pass
                   
                   def isInt123(x: Base) -> TypeIs[Derived]:
                       ...
                   """);
  }

  public void testTypeIsMissedParameter() {
    doTestByText("""
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
                    """);
  }

  public void testTypeGuardMissedParameter() {
    doTestByText("""
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
                    """);
  }

  // PY-71002
  public void testNonDefaultTypeVarsFollowingOnesWithDefaults() {
    doTestByText("""
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
                   """);
  }

  public void testCastCall() {
    doTestByText("""
                   from typing import cast
                   
                   def f(val: object):
                       v0 = cast(int, 10) # ok
                       v1 = cast(list[int], val) # ok
                       v2 = cast('list[float]', val) # ok
                       v3 = cast(<warning descr="Expected a type">1</warning>, val)
                   """);
  }

  public void testIsInstanceAndClassChecksOnNewType() {
    doTestByText("""
                   from typing import NewType
                   
                   UserId = NewType("UserId", int)

                   def f(val):
                       isinstance(val, <error descr="NewType type cannot be used with instance and class checks">UserId</error>)
                       issubclass(int, <error descr="NewType type cannot be used with instance and class checks">UserId</error>)
                   """);
  }

  // PY-75759
  public void testTypeVarDefaultsScoping() {
    doTestByText("""
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
                   """);
  }

  // PY-75759
  public void testParamSpecDefaultScoping() {
    doTestByText("""
                   from typing import ParamSpec, Generic
                   
                   P1 = ParamSpec("P1", default=[int, str])
                   P2 = ParamSpec("P2", default=P1)
                   
                   class Clazz(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">P2</warning>, P1]): ...
                   """);
  }

  // PY-71002
  public void testNonDefaultParamSpecFollowingOnesWithDefaults() {
    doTestByText("""
                   from typing import ParamSpec, Generic
                   
                   P1 = ParamSpec("P1")
                   P2 = ParamSpec("P2", default=[int, str])
                   
                   class Clazz(Generic[P2, <error descr="Non-default TypeVars cannot follow ones with defaults">P1</error>]): ...
                   """);
  }

  // PY-75759
  public void testTypeVarTupleDefaultScoping() {
    doTestByText("""
                   from typing import Generic, TypeVarTuple, Unpack
                   
                   Ts1 = TypeVarTuple("Ts1", default=Unpack[tuple[int, int]])
                   Ts2 = TypeVarTuple("Ts2", default=Unpack[Ts1])
                   
                   class Clazz(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">*Ts2</warning>]): ...
                   """);
  }

  // PY-75759
  public void testTypeVarAllowedDefaultValues() {
    doTestByText("""
                   from typing import TypeVar, Generic
                   
                   T = TypeVar("T", default=<warning descr="Default type must be a type expression">3</warning>)
                   T1 = TypeVar("T1", default=<warning descr="Default type must be a type expression">True</warning>)
                   T3 = TypeVar("T3", default="NormalT")
                   NormalT = TypeVar("NormalT")
                   T4 = TypeVar("T4", default=NormalT)
                   T5 = TypeVar("T5", default=list)
                   class Clazz: ...
                   T6 = TypeVar("T6", default=Clazz)
                   """);
  }

  // PY-75759
  public void testNewStyleTypeVarAllowedDefaultValues() {
    doTestByText("""
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
                   """);
  }

  // PY-75759
  public void testParamSpecAllowedDefaultValues() {
    doTestByText("""
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
                   """);
  }

  // PY-75759
  public void testNewStyleParamSpecAllowedDefaultValues() {
    doTestByText("""
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
                   """);
  }

  // PY-75759
  public void testTypeVarTupleAllowedDefaultValues() {
    doTestByText("""
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
                   """);
  }

  // PY-75759
  public void testNewStyleTypeVarTupleAllowedDefaultValues() {
    doTestByText("""
                   from typing import TypeVar, Generic, ParamSpec, TypeVarTuple, Unpack
                   T1 = TypeVar("T1")
                   Ts1 = TypeVarTuple("Ts1")
                   P1 = ParamSpec("P1")
                   
                   class Clazz[*Ts = Unpack[tuple[int, T1]]]: ...
                   class Clazz[*Ts = <warning descr="Default type of TypeVarTuple must be unpacked">1</warning>]: ...
                   class Clazz[*Ts = <warning descr="Default type of TypeVarTuple must be unpacked">True</warning>]: ...
                   class Clazz[*Ts = <warning descr="Default type of TypeVarTuple must be unpacked">tuple[int]</warning>]: ...
                   class Clazz[*Ts = *tuple[int]]: ...
                   class Clazz[*Ts = Unpack[tuple[int]]]: ...
                   class Clazz[*Ts = <warning descr="Default type of TypeVarTuple must be unpacked">T1</warning>]: ...
                   class Clazz[*Ts = <warning descr="Default type of TypeVarTuple must be unpacked">Ts1</warning>]: ...
                   class Clazz[*Ts = <warning descr="Default type of TypeVarTuple must be unpacked">P1</warning>]: ...
                   class Clazz[*Ts = Unpack[tuple[int, ...]]]: ...
                   """);
  }

  // PY-75759
  public void testTypeVarCannotFollowTypeVarTuple() {
    doTestByText("""
                   from typing import TypeVar, Generic, ParamSpec, TypeVarTuple, Unpack
                   T = TypeVar("T", default = int)
                   Ts = TypeVarTuple("Ts")
                   TsDef = TypeVarTuple("TsDef", default = Unpack[tuple[int, int]])
                   P = ParamSpec("P", default = [str, bool])
                   
                   class Clazz(Generic[Ts, <error descr="TypeVar with a default value cannot follow TypeVarTuple">T</error>]): ...
                   class Clazz1(Generic[TsDef, <error descr="TypeVar with a default value cannot follow TypeVarTuple">T</error>]): ...
                   class Clazz2(Generic[TsDef, P]): ...
                   class Clazz3(Generic[Ts, P]): ...
                   """);
  }

  // PY-75759
  public void testTypeVarParameterizationWithDefaults() {
    doTestByText("""
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
                   """);
  }

  // PY-75759
  public void testTypeVarParameterizationExplicitAnyInDefaults() {
    doTestByText("""
                   from typing import Generic, TypeVar, Any
                   
                   T = TypeVar('T')
                   T1 = TypeVar('T1', default=Any)
                   T2 = TypeVar('T2', default=Any)
                   
                   class Clazz(Generic[T, T1, T2]): ...
                   
                   c = Clazz[int]()
                   c1 = Clazz[int, str]()
                   c2 = Clazz[int, str, bool]()
                   c3 = Clazz[<warning descr="Passed type arguments do not match type parameters [T, T1, T2] of class 'Clazz'">int, str, bool, float</warning>]()
                   """);
  }

  // PY-75759
  public void testTypeVarTupleParameterizationWithDefaults() {
    doTestByText("""
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
                   """);
  }

  // PY-75759
  public void testDefaultParamSpecFollowingTypeVarTuple() {
    doTestByText("""
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
                   """);
  }

  // PY-75759
  public void testTypeVarAndTypeVarTupleParameterizationWithDefaults() {
    doTestByText("""
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
                   """);
  }

  // PY-75759
  public void testNonDefaultTypeVarsFollowingOnesWithDefaultsNewStyle() {
    doTestByText("""
                   class Clazz[NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT = int, DefaultT1 = str]:
                       ...
                   
                   c1 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int</warning>]()
                   c2 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int, str</warning>]()
                   c3 = Clazz[int, str, bool]()
                   c4 = Clazz[int, str, bool, int]()
                   c5 = Clazz[int, str, bool, int, str]()
                   c6 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int, str, bool, int, str, int</warning>]()
                   c7 = Clazz[<warning descr="Passed type arguments do not match type parameters [NoDefaultT2, NoDefaultT3, NoDefaultT4, DefaultT, DefaultT1] of class 'Clazz'">int, str, bool, int, str, int, int</warning>]()
                   """);
  }

  // PY-75759
  public void testTypeParametersOutOfScopeNotReportedMultipleTimes() {
    doTestByText("""
                   from typing import TypeVar, Generic
                   
                   T1 = TypeVar('T1')
                   T2 = TypeVar('T2')
                   T3 = TypeVar('T3', default=T1 | T2)
                   T4 = TypeVar('T4', default=T2)
                   
                   class Clazz1(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">T3</warning>]): ...
                   class Clazz2(Generic[<warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">T3</warning>, <warning descr="Default type of this type parameter refers to one or more type variables that are out of scope">T4</warning>]): ...
                   """);
  }

  // PY-75759
  public void testTypeVarDefaultTypesAreTypeVars() {
    doTestByText("""
                   from typing import TypeVar, TypeVarTuple, ParamSpec
                   
                   type A1[**P, T = <warning descr="'ParamSpec' cannot be used in default type of TypeVar">P</warning>] = tuple[P, T]  # false negative
                   
                   Ts = TypeVarTuple("Ts")
                   T1 = TypeVar("T1", default=<warning descr="'TypeVarTuple' cannot be used in default type of TypeVar">Ts</warning>)
                   
                   P = ParamSpec("P")
                   T2 = TypeVar("T2", default=<warning descr="'ParamSpec' cannot be used in default type of TypeVar">P</warning>)
                   T3 = TypeVar("T3", default=dict[<warning descr="Passed type arguments do not match type parameters [_KT, _VT] of class 'dict'">str, Ts</warning>])
                   T4 = TypeVar("T4", default=dict[list[<warning descr="Passed type arguments do not match type parameters [_T] of class 'list'">P</warning>], str])
                   """);
  }

  // PY-75759
  public void testTypeVarTupleIsNotConsideredMandatoryTypeParameter() {
    doTestByText("""
                   class Clazz[T1, T2, *Ts, T3]: ...
                   
                   c1 = Clazz[<warning descr="Passed type arguments do not match type parameters [T1, T2, *Ts, T3] of class 'Clazz'">int</warning>]()
                   c2 = Clazz[<warning descr="Passed type arguments do not match type parameters [T1, T2, *Ts, T3] of class 'Clazz'">int, str</warning>]()
                   c3 = Clazz[int, str, bool]()
                   c4 = Clazz[int, str, bool, float]()
                   """);
  }

  // PY-75759
  public void testAllowedTypeArguments() {
    doTestByText("""
                   from typing import Literal, TypeAlias
                   
                   class Clazz[T1, T2 = int]: ...
                   
                   var = 1
                   myInt = int
                   type myIntOrStr = int | str
                   myIntAlias: TypeAlias = int
                   
                   class A:...
                   
                   c1 = Clazz[<warning descr="Invalid type argument">print()</warning>, int]()
                   c2 = Clazz[int, <warning descr="Invalid type argument">print()</warning>]()
                   c3 = Clazz[<warning descr="Invalid type argument">1</warning>]
                   c4 = Clazz["int", "str"]
                   c5 = Clazz[dict[int, str]]
                   c7 = Clazz[<warning descr="Invalid type argument">True</warning>]
                   c8 = Clazz[<warning descr="Invalid type argument">list or set</warning>]
                   c9 = Clazz[Literal[3]]
                   c10 = Clazz[<warning descr="Invalid type argument">var</warning>]
                   c11 = Clazz[myInt]
                   c12 = Clazz[myIntOrStr]
                   c13 = Clazz[myIntAlias]
                   c14 = Clazz[A]
                   c15 = Clazz[<warning descr="Invalid type argument">{"a": "b"}</warning>]
                   c16 = Clazz[<warning descr="Invalid type argument">(lambda: int)()</warning>]
                   c17 = Clazz[<warning descr="Invalid type argument">(int, str)</warning>]
                   """);
  }

  // PY-77601, PY-76840
  public void testParamSpecNotMappedToSingleTypeWithoutSquareBrackets() {
    doTestByText("""
                   from typing import Generic, ParamSpec, TypeVar
                   
                   T = TypeVar("T")
                   P1 = ParamSpec("P1")
                   P2 = ParamSpec("P2")
                   
                   class ClassA(Generic[T, P1]): ...
                   
                   x: ClassA[<warning descr="Passed type arguments do not match type parameters [T, **P1] of class 'ClassA'">int, int</warning>]
                   x1: ClassA[int, [int]]
                   """);
  }

  // TODO remove when PY-77059 is supported
  public void testParameterizedImplicitTypeAlias() {
    doTestByText("""
                   from typing import TypeVar, Coroutine, Any
                   T = TypeVar("T")
                   Co = Coroutine[Any, Any, T]
                   MyCo = Co[T]
                   """);
  }


  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypeHintsInspection.class;
  }
}
