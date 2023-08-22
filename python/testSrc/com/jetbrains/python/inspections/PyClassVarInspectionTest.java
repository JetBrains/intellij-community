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
                                              x = 1  <warning descr="'ClassVar' can only be used for assignments in class body"># type: ClassVar[int]</warning>
                                              """));
  }

  public void testCanNotAssignOutsideOfClassWithAnnotation() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              x: <warning descr="'ClassVar' can only be used for assignments in class body">ClassVar[int]</warning> = 1
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
                                                  <warning descr="Cannot override class variable 'x' (previously declared on base class 'A') with instance variable">x</warning> = 2  # type: int"""));
  }

  public void testCanNotOverrideNormalAttributeWithClassVar() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar
                                              class A:
                                                  x = 1  # type: int
                                              class B(A):
                                                  <warning descr="Cannot override instance variable 'x' (previously declared on base class 'A') with class variable">x</warning> = 2  # type: ClassVar[int]"""));
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
                                                  <warning descr="Cannot override instance variable 'x' (previously declared on base class 'B') with class variable">x</warning> = 3  # type: ClassVar[int]"""));
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
                                                      x: <warning descr="'ClassVar' cannot be used in annotations for local variables">ClassVar</warning> = "str\""""));
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
                                                  a = None <warning descr="'ClassVar' parameter cannot include type variables"># type: ClassVar[T]</warning>
                                                  b = None <warning descr="'ClassVar' parameter cannot include type variables"># type: ClassVar[List[Set[T]]]</warning>"""));
  }

  // PY-54540
  public void testCanNotUseTypeVarInTuple() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import ClassVar, TypeVar, Generic, Tuple

                                              T = TypeVar("T")

                                              class A(Generic[T]):
                                                  a: ClassVar[Tuple[int, <warning descr="'ClassVar' parameter cannot include type variables">T</warning>]]
                                                  b = None <warning descr="'ClassVar' parameter cannot include type variables"># type: ClassVar[Tuple[int, T]]</warning>"""));
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
                                                  b = None  <warning descr="'ClassVar' parameter cannot include type variables"># type: ClassVar[tuple[MyType[int, T3], T2]]</warning>"""));
  }

  @Override
  protected @NotNull Class<? extends PyInspection> getInspectionClass() {
    return PyClassVarInspection.class;
  }
}
