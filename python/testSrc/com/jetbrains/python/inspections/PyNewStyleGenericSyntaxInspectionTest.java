// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyNewStyleGenericSyntaxInspectionTest extends PyInspectionTestCase {

  public void testGenericTypeReportedInTypeVarBound() {
    runWithLanguageLevel(LanguageLevel.PYTHON312,
                         () -> doTestByText("""
                                              class ClassC[V]:
                                                  class ClassD[T: dict[str, <error descr="Generic types are not allowed inside constraints and bounds of type parameters">V</error>]]: ...
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

  @Override
  protected @NotNull Class<? extends PyInspection> getInspectionClass() {
    return PyNewStyleGenericSyntaxInspection.class;
  }
}
