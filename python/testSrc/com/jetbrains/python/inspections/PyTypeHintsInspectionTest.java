// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyTypeHintsInspectionTest extends PyInspectionTestCase {

  // PY-28243
  public void testTypeVarAndTargetName() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T0 = TypeVar('T0')\n" +
                 "T1 = TypeVar(<warning descr=\"The argument to 'TypeVar()' must be a string equal to the variable name to which it is assigned\">'T2'</warning>)");
  }

  // PY-28243
  public void testTypeVarPlacement() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import List, TypeVar\n" +
                         "\n" +
                         "T0 = TypeVar('T0')\n" +
                         "a: List[T0]\n" +
                         "b: List[<warning descr=\"A 'TypeVar()' expression must always directly be assigned to a variable\">TypeVar('T1')</warning>]")
    );
  }

  // PY-28243
  public void testTypeVarRedefinition() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T0 = TypeVar('T0')\n" +
                 "print(T0)\n" +
                 "<warning descr=\"Type variables must not be redefined\">T0</warning> = TypeVar('T0')");
  }

  // PY-28124
  public void testTypeVarBivariant() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T1 = <error descr=\"Bivariant type variables are not supported\">TypeVar('T1', contravariant=True, covariant=True)</error>\n" +
                 "true = True\n" +
                 "T2 = <error descr=\"Bivariant type variables are not supported\">TypeVar('T2', contravariant=true, covariant=true)</error>");
  }

  // PY-28124
  public void testTypeVarConstraintsAndBound() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T2 = <error descr=\"Constraints cannot be combined with bound=...\">TypeVar('T2', int, str, bound=str)</error>");
  }

  // PY-28124
  public void testTypeVarConstraintsNumber() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T1 = <error descr=\"A single constraint is not allowed\">TypeVar('T1', int)</error>\n" +
                 "T2 = TypeVar('T2', int, str)");
  }

  // PY-28124
  public void testTypeVarNameAsLiteral() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "name = 'T0'\n" +
                 "T0 = TypeVar(<warning descr=\"'TypeVar()' expects a string literal as first argument\">name</warning>)\n" +
                 "T1 = TypeVar('T1')");
  }

  // PY-28243
  public void testTypeVarParameterizedConstraints() {
    doTestByText("from typing import TypeVar, List\n" +
                 "\n" +
                 "T1 = TypeVar('T1', int, str)\n" +
                 "\n" +
                 "T2 = TypeVar('T2', int, <warning descr=\"Constraints cannot be parametrized by type variables\">List[T1]</warning>)\n" +
                 "T3 = TypeVar('T3', bound=<warning descr=\"Constraints cannot be parametrized by type variables\">List[T1]</warning>)\n" +
                 "\n" +
                 "T4 = TypeVar('T4', int, List[int])\n" +
                 "T5 = TypeVar('T5', bound=List[int])\n" +
                 "\n" +
                 "my_int = int\n" +
                 "my_str = str\n" +
                 "T11 = TypeVar('T11', my_int, my_str)\n" +
                 "\n" +
                 "my_list_t1 = List[T1]\n" +
                 "T22 = TypeVar('T22', int, <warning descr=\"Constraints cannot be parametrized by type variables\">my_list_t1</warning>)\n" +
                 "T33 = TypeVar('T33', bound=<warning descr=\"Constraints cannot be parametrized by type variables\">my_list_t1</warning>)\n" +
                 "\n" +
                 "my_list_int = List[int]\n" +
                 "T44 = TypeVar('T44', int, my_list_int)\n" +
                 "T55 = TypeVar('T55', bound=my_list_int)");
  }

  // PY-28227
  public void testPlainGenericInheritance() {
    doTestByText("from typing import Generic\n" +
                 "\n" +
                 "class A(<error descr=\"Cannot inherit from plain 'Generic'\">Generic</error>):\n" +
                 "    pass\n" +
                 "\n" +
                 "B = Generic\n" +
                 "class C(<error descr=\"Cannot inherit from plain 'Generic'\">B</error>):\n" +
                 "    pass");
  }

  // PY-28227
  public void testGenericParametersTypes() {
    doTestByText("from typing import Generic, TypeVar\n" +
                 "\n" +
                 "class A1(Generic[<error descr=\"Parameters to 'Generic[...]' must all be type variables\">0</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "class B1(Generic[<error descr=\"Parameters to 'Generic[...]' must all be type variables\">int</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "class A2(Generic[<error descr=\"Parameters to 'Generic[...]' must all be type variables\">0</error>, <error descr=\"Parameters to 'Generic[...]' must all be type variables\">0</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "class B2(Generic[<error descr=\"Parameters to 'Generic[...]' must all be type variables\">int</error>, <error descr=\"Parameters to 'Generic[...]' must all be type variables\">int</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "null = 0\n" +
                 "class A3(Generic[<error descr=\"Parameters to 'Generic[...]' must all be type variables\">null</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "my_int = int\n" +
                 "class B3(Generic[<error descr=\"Parameters to 'Generic[...]' must all be type variables\">my_int</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "T = TypeVar('T')\n" +
                 "S = TypeVar('S')\n" +
                 "\n" +
                 "class C1(Generic[T]):\n" +
                 "    pass\n" +
                 "\n" +
                 "class C2(Generic[T, S]):\n" +
                 "    pass\n" +
                 "\n" +
                 "my_t = T\n" +
                 "class C3(Generic[my_t]):\n" +
                 "    pass\n" +
                 "\n" +
                 "class D1:\n" +
                 "    pass\n" +
                 "\n" +
                 "class D2:\n" +
                 "    pass\n" +
                 "\n" +
                 "class E1(Generic[<error descr=\"Parameters to 'Generic[...]' must all be type variables\">D1</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "class F1(Generic[<error descr=\"Parameters to 'Generic[...]' must all be type variables\">D1</error>, <error descr=\"Parameters to 'Generic[...]' must all be type variables\">D2</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "my_d = D1\n" +
                 "class E2(Generic[<error descr=\"Parameters to 'Generic[...]' must all be type variables\">my_d</error>]):\n" +
                 "    pass");
  }

  // PY-28227
  public void testGenericParametersDuplication() {
    doTestByText("from typing import Generic, TypeVar\n" +
                 "\n" +
                 "T = TypeVar('T')\n" +
                 "\n" +
                 "class C(Generic[T, <error descr=\"Parameters to 'Generic[...]' must all be unique\">T</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "B = Generic\n" +
                 "class A(B[T, <error descr=\"Parameters to 'Generic[...]' must all be unique\">T</error>]):\n" +
                 "    pass\n" +
                 "\n" +
                 "T1 = T\n" +
                 "class D(Generic[T1, <error descr=\"Parameters to 'Generic[...]' must all be unique\">T</error>]):\n" +
                 "    pass");
  }

  // PY-28227
  public void testGenericDuplication() {
    doTestByText("from typing import Generic, TypeVar\n" +
                 "\n" +
                 "T = TypeVar('T')\n" +
                 "S = TypeVar('S')\n" +
                 "\n" +
                 "class C(Generic[T], <error descr=\"Cannot inherit from 'Generic[...]' multiple times\">Generic[S]</error>):\n" +
                 "    pass\n" +
                 "\n" +
                 "B = Generic\n" +
                 "class D(B[T], <error descr=\"Cannot inherit from 'Generic[...]' multiple times\">Generic[S]</error>):\n" +
                 "    pass\n" +
                 "\n" +
                 "E = Generic[T]\n" +
                 "class A(E, <error descr=\"Cannot inherit from 'Generic[...]' multiple times\">Generic[S]</error>):\n" +
                 "    pass");
  }

  // PY-28227
  public void testGenericCompleteness() {
    doTestByText("from typing import Generic, TypeVar, Iterable\n" +
                 "\n" +
                 "T = TypeVar('T')\n" +
                 "S = TypeVar('S')\n" +
                 "\n" +
                 "class C<error descr=\"Some type variables (S) are not listed in 'Generic[T]'\">(Generic[T], Iterable[S])</error>:\n" +
                 "    pass\n" +
                 "\n" +
                 "B = Generic\n" +
                 "D = T\n" +
                 "class A<error descr=\"Some type variables (S) are not listed in 'Generic[T]'\">(B[D], Iterable[S])</error>:\n" +
                 "    pass\n" +
                 "\n" +
                 "class E(Generic[T], Iterable[T]):\n" +
                 "    pass\n" +
                 "\n" +
                 "class F(B[D], Iterable[D]):\n" +
                 "    pass\n" +
                 "    \n" +
                 "class G(Iterable[T]):\n" +
                 "    pass");
  }

  // PY-31147
  public void testGenericCompletenessPartiallySpecialized() {
    doTestByText("from typing import TypeVar, Generic, Dict\n" +
                 "\n" +
                 "T = TypeVar(\"T\")\n" +
                 "\n" +
                 "class C(Generic[T], Dict[int, T]):\n" +
                 "    pass");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnAny() {
    doTestByText("from typing import Any\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Any' cannot be used with instance and class checks\">Any</error>)\n" +
                 "B = Any\n" +
                 "assert issubclass(A, <error descr=\"'Any' cannot be used with instance and class checks\">B</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnNoReturn() {
    doTestByText("from typing import NoReturn\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'NoReturn' cannot be used with instance and class checks\">NoReturn</error>)\n" +
                 "B = NoReturn\n" +
                 "assert issubclass(A, <error descr=\"'NoReturn' cannot be used with instance and class checks\">B</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnTypeVar() {
    doTestByText("from typing import TypeVar\n" +
                 "\n" +
                 "T = TypeVar(\"T\")\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), TypeVar)\n" +
                 "assert issubclass(A, TypeVar)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"Type variables cannot be used with instance and class checks\">T</error>)\n" +
                 "assert issubclass(A, <error descr=\"Type variables cannot be used with instance and class checks\">T</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnUnion() {
    doTestByText("from typing import Union\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Union' cannot be used with instance and class checks\">Union</error>)\n" +
                 "B = Union\n" +
                 "assert issubclass(A, <error descr=\"'Union' cannot be used with instance and class checks\">B</error>)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Union' cannot be used with instance and class checks\">Union[int, str]</error>)\n" +
                 "assert issubclass(A, <error descr=\"'Union' cannot be used with instance and class checks\">B[int, str]</error>)\n" +
                 "C = B[int, str]\n" +
                 "assert issubclass(A, <error descr=\"'Union' cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnOptional() {
    doTestByText("from typing import Optional\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Optional' cannot be used with instance and class checks\">Optional</error>)\n" +
                 "B = Optional\n" +
                 "assert issubclass(A, <error descr=\"'Optional' cannot be used with instance and class checks\">B</error>)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Optional' cannot be used with instance and class checks\">Optional[int]</error>)\n" +
                 "assert issubclass(A, <error descr=\"'Optional' cannot be used with instance and class checks\">B[int]</error>)\n" +
                 "C = B[int]\n" +
                 "assert issubclass(A, <error descr=\"'Optional' cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnClassVar() {
    doTestByText("from typing import ClassVar\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'ClassVar' cannot be used with instance and class checks\">ClassVar</error>)\n" +
                 "B = ClassVar\n" +
                 "assert issubclass(A, <error descr=\"'ClassVar' cannot be used with instance and class checks\">B</error>)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'ClassVar' cannot be used with instance and class checks\">ClassVar[int]</error>)\n" +
                 "assert issubclass(A, <error descr=\"'ClassVar' cannot be used with instance and class checks\">B[int]</error>)\n" +
                 "C = B[int]\n" +
                 "assert issubclass(A, <error descr=\"'ClassVar' cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnGeneric() {
    doTestByText("from typing import TypeVar, Generic\n" +
                 "\n" +
                 "T = TypeVar(\"T\")\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Generic' cannot be used with instance and class checks\">Generic</error>)\n" +
                 "B = Generic\n" +
                 "assert issubclass(A, <error descr=\"'Generic' cannot be used with instance and class checks\">B</error>)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Generic' cannot be used with instance and class checks\">Generic[T]</error>)\n" +
                 "assert issubclass(A, <error descr=\"'Generic' cannot be used with instance and class checks\">B[T]</error>)\n" +
                 "C = B[T]\n" +
                 "assert issubclass(A, <error descr=\"'Generic' cannot be used with instance and class checks\">C</error>)");
  }

  // PY-34945
  public void testInstanceAndClassChecksOnFinal() {
    doTestByText("from typing import TypeVar\n" +
                 "from typing_extensions import Final\n" +
                 "\n" +
                 "T = TypeVar(\"T\")\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Final' cannot be used with instance and class checks\">Final</error>)\n" +
                 "B = Final\n" +
                 "assert issubclass(A, <error descr=\"'Final' cannot be used with instance and class checks\">B</error>)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Final' cannot be used with instance and class checks\">Final[T]</error>)\n" +
                 "assert issubclass(A, <error descr=\"'Final' cannot be used with instance and class checks\">B[T]</error>)\n" +
                 "C = B[T]\n" +
                 "assert issubclass(A, <error descr=\"'Final' cannot be used with instance and class checks\">C</error>)");
  }

  // PY-35235
  public void testInstanceAndClassChecksOnLiteral() {
    doTestByText("from typing_extensions import Literal\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Literal' cannot be used with instance and class checks\">Literal</error>)\n" +
                 "B = Literal\n" +
                 "assert issubclass(A, <error descr=\"'Literal' cannot be used with instance and class checks\">B</error>)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"'Literal' cannot be used with instance and class checks\">Literal[1]</error>)\n" +
                 "assert issubclass(A, <error descr=\"'Literal' cannot be used with instance and class checks\">B[1]</error>)\n" +
                 "C = B[1]\n" +
                 "assert issubclass(A, <error descr=\"'Literal' cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnGenericInheritor() {
    doTestByText("from typing import TypeVar, List\n" +
                 "\n" +
                 "T = TypeVar(\"T\")\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), List)\n" +
                 "B = List\n" +
                 "assert issubclass(A, B)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"Parameterized generics cannot be used with instance and class checks\">List[T]</error>)\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">B[T]</error>)\n" +
                 "C = B[T]\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnTuple() {
    doTestByText("from typing import Tuple\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), Tuple)\n" +
                 "B = Tuple\n" +
                 "assert issubclass(A, B)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"Parameterized generics cannot be used with instance and class checks\">Tuple[int, str]</error>)\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">B[int, str]</error>)\n" +
                 "C = B[int, str]\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnType() {
    doTestByText("from typing import Type\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), Type)\n" +
                 "B = Type\n" +
                 "assert issubclass(A, B)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"Parameterized generics cannot be used with instance and class checks\">Type[int]</error>)\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">B[int]</error>)\n" +
                 "C = B[int]\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnCallable() {
    doTestByText("from typing import Callable\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), Callable)\n" +
                 "B = Callable\n" +
                 "assert issubclass(A, B)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"Parameterized generics cannot be used with instance and class checks\">Callable[..., str]</error>)\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">B[..., str]</error>)\n" +
                 "C = B[..., str]\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnProtocol() {
    doTestByText("from typing import Protocol, TypeVar\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "    \n" +
                 "T = TypeVar(\"T\")\n" +
                 "\n" +
                 "assert isinstance(A(), Protocol)\n" +
                 "B = Protocol\n" +
                 "assert issubclass(A, B)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"Parameterized generics cannot be used with instance and class checks\">Protocol[T]</error>)\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">B[T]</error>)\n" +
                 "C = B[T]\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnUserClass() {
    doTestByText("from typing import Generic, TypeVar\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "T = TypeVar(\"T\")    \n" +
                 "\n" +
                 "class D(Generic[T]):\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), D)\n" +
                 "B = D\n" +
                 "assert issubclass(A, B)\n" +
                 "\n" +
                 "assert isinstance(A(), <error descr=\"Parameterized generics cannot be used with instance and class checks\">D[int]</error>)\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">B[int]</error>)\n" +
                 "C = B[int]\n" +
                 "assert issubclass(A, <error descr=\"Parameterized generics cannot be used with instance and class checks\">C</error>)");
  }

  // PY-28249
  public void testInstanceAndClassChecksOnUnknown() {
    doTestByText("from m1 import D\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "assert isinstance(A(), D)\n" +
                 "B = D\n" +
                 "assert issubclass(A, B)\n" +
                 "\n" +
                 "assert isinstance(A(), D[int])\n" +
                 "assert issubclass(A, B[int])\n" +
                 "C = B[int]\n" +
                 "assert issubclass(A, C)");
  }

  // PY-31788
  public void testInstanceAndClassChecksOnGenericParameter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing import List, Type, TypeVar\n" +
                         "\n" +
                         "T = TypeVar(\"T\")\n" +
                         "\n" +
                         "class A:\n" +
                         "    pass\n" +
                         "\n" +
                         "def foo(p1: T, p2: Type[T], p3: List[T]):\n" +
                         "    assert isinstance(A(), <error descr=\"Type variables cannot be used with instance and class checks\">p1</error>)\n" +
                         "    assert issubclass(A, <error descr=\"Type variables cannot be used with instance and class checks\">p1</error>)\n" +
                         "\n" +
                         "    assert isinstance(A(), p2)\n" +
                         "    assert issubclass(A, p2)\n" +
                         "\n" +
                         "    assert isinstance(A(), <error descr=\"Type variables cannot be used with instance and class checks\">p3</error>)\n" +
                         "    assert issubclass(A, <error descr=\"Type variables cannot be used with instance and class checks\">p3</error>)")
    );
  }

  // PY-16853
  public void testParenthesesAndTyping() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing import Union\n" +
                         "\n" +
                         "def a(b: <error descr=\"Generics should be specified through square brackets\">Union(int, str)</error>):\n" +
                         "    pass\n" +
                         "\n" +
                         "def c(d):\n" +
                         "    # type: (<error descr=\"Generics should be specified through square brackets\">Union(int, str)</error>) -> None\n" +
                         "    pass\n" +
                         "\n" +
                         "def e(f: <error descr=\"Generics should be specified through square brackets\">Union()</error>):\n" +
                         "    pass\n" +
                         "\n" +
                         "def g(h):\n" +
                         "    # type: (<error descr=\"Generics should be specified through square brackets\">Union()</error>) -> None\n" +
                         "    pass\n" +
                         "    \n" +
                         "v1 = <error descr=\"Generics should be specified through square brackets\">Union(int, str)</error>\n" +
                         "v2 = None  # type: <error descr=\"Generics should be specified through square brackets\">Union(int, str)</error>\n" +
                         "\n" +
                         "U = Union\n" +
                         "def i(j: <error descr=\"Generics should be specified through square brackets\">U(int, str)</error>):\n" +
                         "    pass\n" +
                         "    \n" +
                         "v3 = <error descr=\"Generics should be specified through square brackets\">U(int, str)</error>\n" +
                         "\n" +
                         "with foo() as bar:  # type: <error descr=\"Generics should be specified through square brackets\">Union(int,str)</error>\n" +
                         "    pass\n" +
                         "    \n" +
                         "for x in []:  # type: <error descr=\"Generics should be specified through square brackets\">Union(int,str)</error>\n" +
                         "    pass")
    );
  }

  // PY-16853
  public void testParenthesesAndCustom() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing import Generic, TypeVar\n" +
                         "\n" +
                         "T = TypeVar(\"T\")\n" +
                         "\n" +
                         "class A(Generic[T]):\n" +
                         "    def __init__(self, v):\n" +
                         "        pass\n" +
                         "\n" +
                         "def a(b: <warning descr=\"Generics should be specified through square brackets\">A(int)</warning>):\n" +
                         "    pass\n" +
                         "\n" +
                         "def c(d):\n" +
                         "    # type: (<warning descr=\"Generics should be specified through square brackets\">A(int)</warning>) -> None\n" +
                         "    pass\n" +
                         "\n" +
                         "def e(f: <warning descr=\"Generics should be specified through square brackets\">A()</warning>):\n" +
                         "    pass\n" +
                         "\n" +
                         "def g(h):\n" +
                         "    # type: (<warning descr=\"Generics should be specified through square brackets\">A()</warning>) -> None\n" +
                         "    pass\n" +
                         "    \n" +
                         "v1 = A(int)\n" +
                         "v2 = None  # type: <warning descr=\"Generics should be specified through square brackets\">A(int)</warning>\n" +
                         "\n" +
                         "U = A\n" +
                         "def i(j: <warning descr=\"Generics should be specified through square brackets\">U(int)</warning>):\n" +
                         "    pass\n" +
                         "    \n" +
                         "v3 = None  # type: <warning descr=\"Generics should be specified through square brackets\">U(int)</warning>")
    );
  }

  // PY-20530
  public void testCallableParameters() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import Callable\n" +
                         "\n" +
                         "a: Callable[..., str]\n" +
                         "b: Callable[[int], str]\n" +
                         "c: Callable[[int, str], str]\n" +
                         "\n" +
                         "d: Callable[<error descr=\"'Callable' must be used as 'Callable[[arg, ...], result]'\">...</error>]\n" +
                         "e: Callable[<error descr=\"'Callable' must be used as 'Callable[[arg, ...], result]'\">int</error>, str]\n" +
                         "f: Callable[<error descr=\"'Callable' must be used as 'Callable[[arg, ...], result]'\">int, str</error>, str]\n" +
                         "g: Callable[<error descr=\"'Callable' must be used as 'Callable[[arg, ...], result]'\">(int, str)</error>, str]\n" +
                         "h: Callable[<error descr=\"'Callable' must be used as 'Callable[[arg, ...], result]'\">int</error>]\n" +
                         "h: Callable[<error descr=\"'Callable' must be used as 'Callable[[arg, ...], result]'\">(int)</error>, str]")
    );
  }

  // PY-20530
  public void testSelf() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("class A:\n" +
                         "    def method(self, i: int):\n" +
                         "        v1: <error descr=\"Invalid type 'self'\">self</error>.B\n" +
                         "        v2 = None  # type: <error descr=\"Invalid type 'self'\">self</error>.B\n" +
                         "        print(self.B)\n" +
                         "\n" +
                         "    class B:\n" +
                         "        pass\n" +
                         "\n" +
                         "class self:\n" +
                         "    pass\n" +
                         "\n" +
                         "v: self")
    );
  }

  // PY-20530
  public void testTupleUnpacking() {
    doTestByText("a1 = undefined()  # type: int\n" +
                 "\n" +
                 "b1, (c1, d1) = undefined()  # type: int, (int, str)\n" +
                 "e1, (f1, g1), h1 = undefined()  # type: int, (str, int), str\n" +
                 "\n" +
                 "b2, (c2, d2) = undefined()  # type: <warning descr=\"Type comment cannot be matched with unpacked variables\">int, (int)</warning>\n" +
                 "e2, (f2, g2), h2 = undefined()  # type: <warning descr=\"Type comment cannot be matched with unpacked variables\">int, (str), str</warning>");
  }

  // PY-20530
  public void testAnnotationAndTypeComment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText(
        "a<warning descr=\"Type(s) specified both in type comment and annotation\">: int</warning> = None  <warning descr=\"Type(s) specified both in type comment and annotation\"># type: int</warning>\n" +
        "\n" +
        "def foo(a<warning descr=\"Type(s) specified both in type comment and annotation\">: int</warning>  <warning descr=\"Type(s) specified both in type comment and annotation\"># type: int</warning>\n" +
        "        ,):\n" +
        "    pass\n" +
        "\n" +
        "def <warning descr=\"Type(s) specified both in type comment and annotation\">bar</warning>(a: int) -> int:\n" +
        "    <warning descr=\"Type(s) specified both in type comment and annotation\"># type: (int) -> int</warning>\n" +
        "    pass\n" +
        "    \n" +
        "def <warning descr=\"Type(s) specified both in type comment and annotation\">baz1</warning>(a: int):\n" +
        "    <warning descr=\"Type(s) specified both in type comment and annotation\"># type: (int) -> int</warning>\n" +
        "    pass\n" +
        "    \n" +
        "def <warning descr=\"Type(s) specified both in type comment and annotation\">baz2</warning>(a) -> int:\n" +
        "    <warning descr=\"Type(s) specified both in type comment and annotation\"># type: (int) -> int</warning>\n" +
        "    pass"
      )
    );
  }

  // PY-20530
  public void testValidTypeCommentAndParameters() {
    doTestByText("from typing import Type\n" +
                 "\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "\n" +
                 "class Bar(A):\n" +
                 "    # self is specified\n" +
                 "    def spam11(self):\n" +
                 "        # type: (Bar) -> None\n" +
                 "        pass\n" +
                 "\n" +
                 "    def egg11(self, a, b):\n" +
                 "        # type: (Bar, str, bool) -> None\n" +
                 "        pass\n" +
                 "        \n" +
                 "    # self is specified\n" +
                 "    def spam12(self):\n" +
                 "        # type: (A) -> None\n" +
                 "        pass\n" +
                 "\n" +
                 "    def egg12(self, a, b):\n" +
                 "        # type: (A, str, bool) -> None\n" +
                 "        pass\n" +
                 "        \n" +
                 "    # self is not specified\n" +
                 "    def spam2(self):\n" +
                 "        # type: () -> None\n" +
                 "        pass\n" +
                 "\n" +
                 "    def egg2(self, a, b):\n" +
                 "        # type: (str, bool) -> None\n" +
                 "        pass\n" +
                 "        \n" +
                 "    # cls is not specified \n" +
                 "    @classmethod\n" +
                 "    def spam3(cls):\n" +
                 "        # type: () -> None\n" +
                 "        pass\n" +
                 "\n" +
                 "    @classmethod\n" +
                 "    def egg3(cls, a, b):\n" +
                 "        # type: (str, bool) -> None\n" +
                 "        pass\n" +
                 "    \n" +
                 "    # cls is specified    \n" +
                 "    @classmethod\n" +
                 "    def spam41(cls):\n" +
                 "        # type: (Type[Bar]) -> None\n" +
                 "        pass\n" +
                 "\n" +
                 "    @classmethod\n" +
                 "    def egg41(cls, a, b):\n" +
                 "        # type: (Type[Bar], str, bool) -> None\n" +
                 "        pass\n" +
                 "    \n" +
                 "    # cls is specified    \n" +
                 "    @classmethod\n" +
                 "    def spam42(cls):\n" +
                 "        # type: (Type[A]) -> None\n" +
                 "        pass\n" +
                 "\n" +
                 "    @classmethod\n" +
                 "    def egg42(cls, a, b):\n" +
                 "        # type: (Type[A], str, bool) -> None\n" +
                 "        pass\n" +
                 "    \n" +
                 "    @staticmethod\n" +
                 "    def spam5():\n" +
                 "        # type: () -> None\n" +
                 "        pass\n" +
                 "\n" +
                 "    @staticmethod\n" +
                 "    def egg5(a, b):\n" +
                 "        # type: (str, bool) -> None\n" +
                 "        pass\n" +
                 "        \n" +
                 "    def baz(self, a, b, c, d):\n" +
                 "        # type: (...) -> None\n" +
                 "        pass");
  }

  // PY-20530
  public void testInvalidTypeCommentAndParameters() {
    doTestByText("from typing import Type\n" +
                 "\n" +
                 "class Bar:\n" +
                 "    # self is specified\n" +
                 "    def spam1(self):\n" +
                 "        <warning descr=\"Type signature has too many arguments\"># type: (Bar, int) -> None</warning>\n" +
                 "        pass\n" +
                 "\n" +
                 "    def egg11(self, a, b):\n" +
                 "        <warning descr=\"Type signature has too many arguments\"># type: (Bar, int, str, bool) -> None</warning>\n" +
                 "        pass\n" +
                 "\n" +
                 "    def egg12(self, a, b):\n" +
                 "        <warning descr=\"Type signature has too few arguments\"># type: (Bar) -> None</warning>\n" +
                 "        pass\n" +
                 "        \n" +
                 "    # self is not specified\n" +
                 "    def spam2(self):\n" +
                 "        <warning descr=\"The type of self 'int' is not a supertype of its class 'Bar'\"># type: (int) -> None</warning>\n" +
                 "        pass\n" +
                 "\n" +
                 "    def egg2(self, a, b):\n" +
                 "        <warning descr=\"The type of self 'int' is not a supertype of its class 'Bar'\"># type: (int, str, bool) -> None</warning>\n" +
                 "        pass\n" +
                 "        \n" +
                 "    # cls is not specified \n" +
                 "    @classmethod\n" +
                 "    def spam3(cls):\n" +
                 "        <warning descr=\"The type of self 'int' is not a supertype of its class 'Type[Bar]'\"># type: (int) -> None</warning>\n" +
                 "        pass\n" +
                 "\n" +
                 "    @classmethod\n" +
                 "    def egg3(cls, a, b):\n" +
                 "        <warning descr=\"The type of self 'int' is not a supertype of its class 'Type[Bar]'\"># type: (int, str, bool) -> None</warning>\n" +
                 "        pass\n" +
                 "    \n" +
                 "    # cls is specified    \n" +
                 "    @classmethod\n" +
                 "    def spam4(cls):\n" +
                 "        <warning descr=\"Type signature has too many arguments\"># type: (Type[Bar], int) -> None</warning>\n" +
                 "        pass\n" +
                 "\n" +
                 "    @classmethod\n" +
                 "    def egg41(cls, a, b):\n" +
                 "        <warning descr=\"Type signature has too many arguments\"># type: (Type[Bar], int, str, bool) -> None</warning>\n" +
                 "        pass\n" +
                 "\n" +
                 "    @classmethod\n" +
                 "    def egg42(cls, a, b):\n" +
                 "        <warning descr=\"Type signature has too few arguments\"># type: (Type[Bar]) -> None</warning>\n" +
                 "        pass\n" +
                 "    \n" +
                 "    @staticmethod\n" +
                 "    def spam5():\n" +
                 "        <warning descr=\"Type signature has too many arguments\"># type: (int) -> None</warning>\n" +
                 "        pass\n" +
                 "\n" +
                 "    @staticmethod\n" +
                 "    def egg51(a, b):\n" +
                 "        <warning descr=\"Type signature has too many arguments\"># type: (int, str, bool) -> None</warning>\n" +
                 "        pass\n" +
                 "\n" +
                 "    @staticmethod\n" +
                 "    def egg52(a, b):\n" +
                 "        <warning descr=\"Type signature has too few arguments\"># type: (int) -> None</warning>\n" +
                 "        pass");
  }

  // PY-20530
  public void testTypingMemberParameters() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText(
        "from typing import Callable, List\n" +
        "\n" +
        "foo1: Callable[[int], <error descr=\"Parameters to generic types must be types\">[int]</error>] = None\n" +
        "foo2: Callable[[int], <error descr=\"Parameters to generic types must be types\">[int, str]</error>] = None\n" +
        "foo3: List[<error descr=\"Parameters to generic types must be types\">[int]</error>]\n" +
        "foo4: List[<error descr=\"Parameters to generic types must be types\">[int, str]</error>]\n" +
        "\n" +
        "l1 = [int]\n" +
        "l2 = [int, str]\n" +
        "\n" +
        "foo5: Callable[[int], <error descr=\"Parameters to generic types must be types\">l1</error>] = None\n" +
        "foo6: Callable[[int], <error descr=\"Parameters to generic types must be types\">l2</error>] = None\n" +
        "foo7: List[<error descr=\"Parameters to generic types must be types\">l1</error>]\n" +
        "foo8: List[<error descr=\"Parameters to generic types must be types\">l2</error>]"
      )
    );
  }

  // PY-32530
  public void testAnnotationAndIgnoreComment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("def foo(a: str):  # type: ignore\n" +
                         "    pass")
    );
  }

  public void testAnnotatingNonSelfAttribute() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("class A:\n" +
                         "    def method(self, b):\n" +
                         "        <warning descr=\"Non-self attribute could not be type hinted\">b.a</warning>: int = 1\n" +
                         "\n" +
                         "class B:\n" +
                         "    pass\n" +
                         "\n" +
                         "<warning descr=\"Non-self attribute could not be type hinted\">B.a</warning>: str = \"2\"\n" +
                         "\n" +
                         "def func(a):\n" +
                         "    <warning descr=\"Non-self attribute could not be type hinted\">a.xxx</warning>: str = \"2\"")
    );
  }

  // PY-35235
  public void testLiteral() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Literal\n" +
                         "\n" +
                         "a: Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">1 + 2</warning>]\n" +
                         "b: Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">4j</warning>]\n" +
                         "c: Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">3.14</warning>]\n" +
                         "d: Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">...</warning>]\n" +
                         "\n" +
                         "class A:\n" +
                         "    pass\n" +
                         "\n" +
                         "e: Literal[Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">A</warning>]]\n" +
                         "f = Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">A</warning>]\n" +
                         "g: Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">f</warning>]")
    );
  }

  // PY-35235
  public void testLiteralWithoutArguments() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import Literal\n" +
                         "a: <warning descr=\"'Literal' must have at least one parameter\">Literal</warning> = 1\n" +
                         "b = 2  # type: <warning descr=\"'Literal' must have at least one parameter\">Literal</warning>")
    );
  }

  // PY-35235
  public void testNonPlainStringAsTypingLiteralIndex() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import Literal\n" +
                         "a: Literal[<warning descr=\"'Literal' may be parameterized with literal ints, byte and unicode strings, bools, Enum values, None, other literal types, or type aliases to other literal types\">f\"1\"</warning>] = \"1\"")
    );
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypeHintsInspection.class;
  }
}
