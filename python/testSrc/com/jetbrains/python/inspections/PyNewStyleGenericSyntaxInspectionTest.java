// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyNewStyleGenericSyntaxInspectionTest extends PyInspectionTestCase {

  public void testGenericTypeReportedInTypeVarBound() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              class ClassA[V]:
                                                  class ClassB[T: dict[str, <error descr="Generic types are not allowed inside constraints and bounds of type parameters">V</error>]]: ...
                                              
                                              class ClassC[**P, T: <error descr="Generic types are not allowed inside constraints and bounds of type parameters">P</error>]: ...
                                              class ClassD[*Ts, T: <error descr="Generic types are not allowed inside constraints and bounds of type parameters">Ts</error>]: ...
                                              """));
  }

  public void testOldStyleTypeVarReportedInSuperClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              from typing import TypeVar
                                              K = TypeVar("K")
                                                                                                                     
                                              class ClassA[V](dict[<error descr="Mixing traditional and new-style type variables is not allowed">K</error>, V]): ...
                                              """));
  }

  public void testExtendingGenericReportedInClassWithTypeParameterList() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              from typing import TypeVar, Generic
                                              class ClassA[T](<warning descr="Classes with an explicit type parameter list should not extend 'Generic'">Generic[T]</warning>): ...\s
                                              """));
  }

  public void testParameterizingExtendedProtocolReportedInClassWithTypeParameterList() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              from typing import Protocol
                                              class ClassA[T](Protocol[<warning descr="Extending 'Protocol' does not need parameterization in classes with a type parameter list">T</warning>]): ...
                                              """));
  }

  public void testStringLiteralNotReportedInTypeParameterBound() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              class ClassB[T: "ForwardReference"]: ...  # OK
                                              """));
  }

  public void testOldStyleTypeVarReportedInTypeAliasStatement() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              from typing import TypeVar
                                              T = TypeVar('T')
                                              
                                              type m = list[<error descr="Traditional TypeVars are not allowed inside new-style type alias statements">T</error>]
                                              """));
  }

  public void testOldStyleTypeVarReportedInTypeAliasStatementWithTypeParameterList() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              from typing import TypeVar
                                              T = TypeVar('T')
                                              
                                              type m[K] = dict[K, <error descr="Traditional TypeVars are not allowed inside new-style type alias statements">T</error>]
                                              """));
  }



  public void testOldStyleTypeVarReportedInParameterListOfFunctionWithTypeParameterList() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              from typing import TypeVar
                                              K = TypeVar("K")
                                                                                            
                                              class ClassC[V]:
                                              
                                                  def method2[M](self, a: M, b: <error descr="Mixing traditional and new-style type variables is not allowed">K</error>): ...
                                              """));
  }

  public void testMixingOldStyleAndNewStyleTypeParametersIsOkInFunctionWithoutTypeParameterList() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              from typing import TypeVar
                                              K = TypeVar("K")
                                                                                            
                                              class ClassC[V]:
                                                  def method1(self, a: V, b: K) -> V | K: ...
                                              """));
  }

  public void testAssignmentExpressionReportedInsideClassDeclarationWithTypeParameterList() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              class ClassA[T]((<error descr="Assignment expressions are not allowed inside declarations of classes, functions and type aliases having type parameter list">x := Sequence[T]</error>)): ...
                                              """));
  }

  public void testAssignmentExpressionReportedInsideFunctionDeclarationWithTypeParameterList() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              def func1[T](val: (<error descr="Assignment expressions are not allowed inside declarations of classes, functions and type aliases having type parameter list">x := int</error>)): ...
                                              """));
  }

  public void testAssignmentExpressionReportedInsideFunctionReturnTypeAnnotationWithTypeParameterList() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              def func1[T](val: (<error descr="Assignment expressions are not allowed inside declarations of classes, functions and type aliases having type parameter list">x := int</error>)): ...
                                              """));
  }

  public void testAssignmentExpressionReportedInsideNewStyleTypeAliasDeclaration() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              type Alias1[T] = (<error descr="Assignment expressions are not allowed inside declarations of classes, functions and type aliases having type parameter list">x := list[T]</error>)
                                              """));
  }

  public void testAssignmentExpressionNotReportedInFunctionParamDefaultValue() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              def f[T](x: int = (foo := 42)): ...
                                              """));
  }

  // PY-71002
  public void testNonDefaultTypeVarsFollowingOnesWithDefaults() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              type Alias[DefaultT = int, <error descr="Non-default TypeVars cannot follow ones with defaults">T</error>] = tuple[DefaultT, T]
                                              def generic_func[DefaultT = int, <error descr="Non-default TypeVars cannot follow ones with defaults">T</error>](x: DefaultT, y: T) -> None: ...
                                              class GenericClass[DefaultT = int, <error descr="Non-default TypeVars cannot follow ones with defaults">T</error>]: ...
                                              class GenericClassTwo[T]: ...
                                              class GenericClassThree[T = int]: ...
                                              class GenericClassThree[T, T1, T2 = int]: ...
                                              """));
  }

  // PY-75759
  public void testTypeVarCannotFollowTypeVarTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
     () -> doTestByText("""
                          class ClassA[*Ts, <error descr="TypeVar with a default value cannot follow TypeVarTuple">T = int</error>]: ...
                          class ClassB[*Ts = *tuple[int], <error descr="TypeVar with a default value cannot follow TypeVarTuple">T = int</error>]: ...
                          class ClassC[*Ts, **P = [float, bool]]: ...
                          class ClassD[*Ts, **P]: ...
                          """));
  }

  // PY-75759
  public void testTypeVarDefaultsOutOfScopeReported() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                          from typing import Callable, Concatenate
                          
                          class A[K]:
                              def m[T = dict[int, <warning descr="Type variable 'K' is out of scope">K</warning>]](self):
                                  pass
                          
                              def outer[T](self):
                                  def inner[X = <warning descr="Type variable 'T' is out of scope">T</warning>]():
                                      pass
                          
                          class B[**P]:
                              def apply[T = Callable[Concatenate[int, <warning descr="Type variable 'P' is out of scope">P</warning>], int]](self):
                                  pass
                          
                          class C[T]:
                              class D[V = <warning descr="Type variable 'T' is out of scope">T</warning>]: ...
                          """));
  }

  // PY-75759
  public void testTypeVarDefaultsOutOfScopeReportedForReferencesToOldStyleTypeVars() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                          from typing import TypeVar, Generic
                          
                          T1 = TypeVar('T1')
                          T2 = TypeVar('T2')
                          T3 = TypeVar('T3', default=T1 | T2)
                          
                          class Clazz1[R = <warning descr="Type variable 'T1' is out of scope">T1</warning> | <warning descr="Type variable 'T2' is out of scope">T2</warning>]: ...
                          """));
  }

  // PY-75759
  public void testTypeVarDefaultOutOfScopeReportedForParamSpecs() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                          from typing import TypeVar, ParamSpec
                          T1 = TypeVar("T1")
                          P1 = ParamSpec("P1")
                          
                          class Clazz1[**P = [<warning descr="Type variable 'T1' is out of scope">T1</warning>]]: ...
                          class Clazz2[**P = <warning descr="Type variable 'P1' is out of scope">P1</warning>]: ...
                          class Clazz3[**P = [int, list[<warning descr="Type variable 'T1' is out of scope">T1</warning>]]]: ...
                          """));
  }


  @Override
  protected @NotNull Class<? extends PyInspection> getInspectionClass() {
    return PyNewStyleGenericSyntaxInspection.class;
  }
}
