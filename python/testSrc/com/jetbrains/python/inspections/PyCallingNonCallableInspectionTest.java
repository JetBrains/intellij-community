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

                           def deco(func: F):
                               func()""")
    );
  }

  // PY-41676
  public void testThereIsNoInspectionOnCallProtectedByHasattr() {
    doTestByText("""
                   def test(obj):
                       if hasattr(obj, "anything"):
                           pkgs = obj.anything()""");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyCallingNonCallableInspection.class;
  }
}
