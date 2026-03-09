// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;


public class PyCallingNonCallableInspectionTest extends PyInspectionTestCase {
  public void testTupleNonCallable() {
    doTest();
  }

  public void testStaticMeth() {
    doTest();
  }

  public void testConcealer() {
    doTest();
  }

  public void testModule() {
    doTest();
  }

  public void testClassAsCallable() {  // PY-4061
    doTest();
  }

  public void testClassAssignments() {  // PY-4061
    doTest();
  }

  public void testNamedTupleCallable() {
    doTest();
  }

  // PY-3892
  public void _testCallableCheck() {
    doTest();
  }

  public void testClassMethodFirstParam() {
    doTest();
  }

  // PY-4090
  public void testDecorators() {
    doTest();
  }

  // PY-4608
  public void testUnionType() {
    doTest();
  }

  // PY-8416
  public void testCallAttributeAssignment() {
    doTest();
  }

  // PY-5905
  public void testCallableClassDecorator() {
    doTest();
  }

  // PY-8182
  public void testGetattrCallable() {
    doTest();
  }

  // PY-8801
  public void testQualifiedNamedTuple() {
    doTest();
  }

  // PY-13051
  public void testCallDictSubscriptionExpression() {
    doTest();
  }

  // PY-12004
  public void testLocalCallableClass() {
    doTest();
  }

  public void testStructuralType() {
    doTest();
  }

  // PY-26555
  public void testAfterModifierWrappingCall() {
    doTest();
  }

  // PY-28626
  public void testFunctionDecoratedAsContextManager() {
    doTest();
  }

  // PY-24161
  public void testGenericClassObjectTypeAnnotation() {
    doTest();
  }

  // PY-24161
  public void testExplicitClassObjectTypeAnnotation() {
    doTest();
  }

  // PY-31943
  public void testTypeVarBoundedWithCallable() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing import TypeVar, Callable, Any

                           F = TypeVar('F', bound=Callable[[], Any])
                           Int = TypeVar('Int', bound=int)

                           def deco(func: F, non_func: Int):
                               func()
                               <warning descr="'non_func' is not callable">non_func()</warning>""")
    );
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing import TypeVar, Callable, Any

                           F = TypeVar('F', Callable[[], Any], Callable[[], int])
                           IntOrFloat = TypeVar('IntOrFloat', int, float)

                           def deco(func: F, non_func: IntOrFloat):
                               func()
                               <warning descr="'non_func' is not callable">non_func()</warning>""")
    );
  }

  // PY-41676
  public void testThereIsNoInspectionOnCallProtectedByHasattr() {
    doTestByText("""
                   def test(obj):
                       if hasattr(obj, "anything"):
                           pkgs = obj.anything()""");
  }

  // PY-85470
  public void testExplicitTypeAliasCallability() {
    doTestByText("""
                   from typing import TypeAlias as TA
                   
                   ExplicitUnionAlias: TA = list | set
                   ExplicitSingleAlias: TA = list
                   a = <warning descr="'ExplicitUnionAlias' is not callable">ExplicitUnionAlias()</warning>
                   b = ExplicitSingleAlias()""");
  }

  // PY-76839
  public void testImplicitTypeAliasCallability() {
    fixme(
      "PY-76839",
      AssertionError.class,
      "'ImplicitUnionAlias' is not callable",
      () -> doTestByText("""
                           ImplicitUnionAlias = list | set
                           ImplicitSingleAlias = list
                           a = <warning descr="'ImplicitUnionAlias' is not callable">ImplicitUnionAlias()</warning>
                           b = ImplicitSingleAlias()""")
    );
  }

  // PY-76851
  public void testTypeStatementAliasCallability() {
    doTestByText("""
                   type UnionAliasStatement = list | set
                   type SingleAliasStatement = list
                   a = <warning descr="'TypeAliasType' object is not callable">UnionAliasStatement()</warning>
                   b = <warning descr="'TypeAliasType' object is not callable">SingleAliasStatement()</warning>""");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyCallingNonCallableInspection.class;
  }
}
