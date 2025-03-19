// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyClassVarInspectionTest extends PyInspectionTestCase {

  public void testCanAssignOnClassAttribute() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = 1  # type: ClassVar[int]
                                              A.x = 2"""));
  }

  public void testCanNotAssignOnInstance() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = 1  # type: ClassVar[int]
                                              <warning descr="Cannot assign to class variable 'x' via instance">A().x</warning> = 2"""));
  }

  public void testCanNotAssignOutsideOfClassWithTypeComment() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              x = 1  <warning descr="'ClassVar' can only be used in class body"># type: ClassVar[int]</warning>
                                              """));
  }

  public void testCanNotAssignOutsideOfClassWithAnnotation() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              x: <warning descr="'ClassVar' can only be used in class body">ClassVar[int]</warning> = 1
                                              """));
  }

  public void testCannotAssignOnSubclassInstance() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = 1  # type: ClassVar[int]
                                              class B(A):
                                                  pass
                                              <warning descr="Cannot assign to class variable 'x' via instance">B().x</warning> = 2"""));
  }

  public void testCanNotOverrideOnSelf() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = None  # type: ClassVar[int]
                                                  def __init__(self) -> None:
                                                      <warning descr="Cannot assign to class variable 'x' via instance">self.x</warning> = 1"""));
  }

  public void testCanNotOverrideOnSelfInSubclass() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = None  # type: ClassVar[int]
                                              class B(A):
                                                  def __init__(self) -> None:
                                                      <warning descr="Cannot assign to class variable 'x' via instance">self.x</warning> = 0"""));
  }

  public void testCanNotAssignOnClassInstanceFromType() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, Type
                                              class A:
                                                  x = None  # type: ClassVar[int]
                                              def f(a: Type[A]) -> None:
                                                  <warning descr="Cannot assign to class variable 'x' via instance">a().x</warning> = 0"""));
  }

  public void testCanAssignOnClassObjectFromType() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, Type
                                              class A:
                                                  x = None  # type: ClassVar[int]
                                              def f(a: Type[A]) -> None:
                                                  a.x = 0"""));
  }

  public void testCanNotOverrideClassVarWithNormalAttribute() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = 1  # type: ClassVar[int]
                                              class B(A):
                                                  <warning descr="Cannot override class variable 'x' (previously declared in base class 'A') with instance variable">x</warning> = 2  # type: int"""));
  }

  public void testCanNotOverrideNormalAttributeWithClassVar() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = 1  # type: int
                                              class B(A):
                                                  <warning descr="Cannot override instance variable 'x' (previously declared in base class 'A') with class variable">x</warning> = 2  # type: ClassVar[int]"""));
  }

  public void testOverrideClassVarWithImplicitThenExplicitMultiFile() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doMultiFileTest);
  }


  public void testCanNotOverrideMultiBaseClassVar() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = 1  # type: ClassVar[int]
                                              class B:
                                                  x = 2  # type: int
                                              class C(A, B):
                                                  <warning descr="Cannot override instance variable 'x' (previously declared in base class 'B') with class variable">x</warning> = 3  # type: ClassVar[int]"""));
  }

  public void testCanOverrideClassVarWithImplicitClassVar() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = 1  # type: ClassVar[int]
                                              class B(A):
                                                  x = 2"""));
  }

  public void testOverrideClassVarWithImplicitThenExplicit() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = 1  # type: ClassVar[int]
                                              class B(A):
                                                  x = 2
                                              class C(B):
                                                  x = 3
                                              class D(C):
                                                  x = 4  # type: ClassVar[int]"""));
  }

  public void testClassVarCanNotBeUsedAsFunctionParameterAnnotation() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar

                                              def foo(a: <warning descr="'ClassVar' cannot be used in annotations for function parameters">ClassVar</warning>):
                                                  pass"""));
  }

  public void testClassVarCanNotBeUsedAsFunctionReturnParameter() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar

                                              def foo() ->  <warning descr="'ClassVar' cannot be used in annotation for a function return value">ClassVar</warning>:
                                                  pass"""));
  }

  public void testClassVarCanNotBeDeclaredInFunctionBody() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class Cls:
                                                  def foo(self):
                                                      x: <warning descr="'ClassVar' can only be used in class body">ClassVar</warning> = "str\""""));
  }

  // PY-54540
  public void testCanNotUseTypeVarInAnnotation() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, TypeVar, Generic, List, Set

                                              T = TypeVar("T")

                                              class A(Generic[T]):
                                                  a: ClassVar[<warning descr="'ClassVar' parameter cannot include type variables">T</warning>]
                                                  b: ClassVar[List[Set[<warning descr="'ClassVar' parameter cannot include type variables">T</warning>]]]"""));
  }

  // PY-54540
  public void testCanNotUseTypeVarInTypeComment() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, TypeVar, Generic, List, Set

                                              T = TypeVar("T")

                                              class A(Generic[T]):
                                                  a = None # type: ClassVar[<warning descr="'ClassVar' parameter cannot include type variables">T</warning>]
                                                  b = None # type: ClassVar[List[Set[<warning descr="'ClassVar' parameter cannot include type variables">T</warning>]]]"""));
  }

  // PY-54540
  public void testCanNotUseTypeVarInTuple() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, TypeVar, Generic, Tuple

                                              T = TypeVar("T")

                                              class A(Generic[T]):
                                                  a: ClassVar[Tuple[int, <warning descr="'ClassVar' parameter cannot include type variables">T</warning>]]
                                                  b = None # type: ClassVar[Tuple[int, <warning descr="'ClassVar' parameter cannot include type variables">T</warning>]]"""));
  }

  // PY-54540
  public void testTypeVarInComplexType() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, TypeVar, Generic

                                              T1 = TypeVar('T1')
                                              T2 = TypeVar('T2')
                                              T3 = TypeVar('T3')

                                              class MyType(Generic[T1, T2]):
                                                  pass

                                              class A:
                                                  a: ClassVar[tuple[MyType[int, <warning descr="'ClassVar' parameter cannot include type variables">T3</warning>], <warning descr="'ClassVar' parameter cannot include type variables">T2</warning>]]
                                                  b = None  # type: ClassVar[tuple[MyType[int, <warning descr="'ClassVar' parameter cannot include type variables">T3</warning>], <warning descr="'ClassVar' parameter cannot include type variables">T2</warning>]]"""));
  }

  // PY-76913
  public void testClassVarAcceptsOnlyOneArgument() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              
                                              class Clazz:
                                                  x: ClassVar[<warning descr="'ClassVar' can only be parameterized with one type">int, int</warning>]
                                              """));
  }

  // PY-76913
  public void testClassVarParameterizationIllegalType() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              
                                              class Clazz:
                                                  x: ClassVar[<warning descr="Not a valid type">3</warning>]
                                                  y: ClassVar[<warning descr="Not a valid type">"abc"</warning>]
                                              """));
  }

  // PY-76913
  public void testClassVarContainsParamSpec() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, ParamSpec, Callable, Any
                                              P = ParamSpec("P")
                                              class Clazz:
                                                  x: ClassVar[Callable[<warning descr="'ClassVar' parameter cannot include type variables">P</warning>, Any]]
                                              """));
  }

  // PY-76913
  public void testClassVarCannotBeNested() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, Final
                                              
                                              class Clazz:
                                                  x: Final[<warning descr="'ClassVar' cannot be nested">ClassVar[int]</warning>]
                                                  y: list[<warning descr="'ClassVar' cannot be nested">ClassVar[int]</warning>]
                                                  z: list[<warning descr="'ClassVar' cannot be nested">ClassVar</warning>]
                                                  a # type: Final[<warning descr="'ClassVar' cannot be nested">ClassVar[int]</warning>]
                                                  b: dict[int, list[<warning descr="'ClassVar' cannot be nested">ClassVar[int]</warning>]]
                                              """));
  }

  // PY-76913
  public void testClassVarCannotBeNestedNotReportedInsideTypingAnnotated() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import Annotated, ClassVar
                                              
                                              class Clazz:
                                                  x: Annotated[ClassVar[list[int]], ""]
                                              """));
  }

  // PY-76913
  public void testSelfAnnotatedWithClassVar() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              
                                              class Clazz:
                                                  def __init__(self) -> None:
                                                      self.x: <warning descr="'ClassVar' can only be used in class body">ClassVar[int]</warning> = 1
                                              """));
  }

  // PY-76913
  public void testClassVarParameterizedWithSelfTypeNotReported() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, Self
                                              
                                              class Clazz:
                                                  x: ClassVar[Self]
                                                  y: ClassVar[dict[str, Self]]
                                              """));
  }

  // PY-76913
  public void testClassVarIsNotAllowedInTypeAlias() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, TypeAlias
                                              
                                              class Clazz:
                                                  x: TypeAlias = <warning descr="'ClassVar' is not allowed here">ClassVar[int]</warning>
                                              """));
  }

  @Override
  protected @NotNull Class<? extends PyInspection> getInspectionClass() {
    return PyClassVarInspection.class;
  }
}
