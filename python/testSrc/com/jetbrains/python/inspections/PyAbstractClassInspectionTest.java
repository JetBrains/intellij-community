// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyAbstractClassInspectionTest extends PyInspectionTestCase {

  public void testAbstract() {
    doTest();
  }

  public void testOverriddenAsField() {
    doTest();
  }

  // PY-38680
  public void testClassWithMethodWhichRaisesNotImplementedErrorNotTreatedAsAbstract() {
    doTest();
  }

  // PY-16035
  public void testHiddenForAbstractSubclassWithExplicitMetaclass() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  // PY-16035
  public void testHiddenForAbstractSubclassWithExplicitMetaclassPy3() {
    doTest();
  }

  // PY-16035
  public void testHiddenForAbstractSubclassWithAbstractMethod() {
    doTest();
  }

  // PY-16776
  public void testNotImplementedOverriddenInParent() {
    doTest();
  }

  public void testConditionalRaiseReturnInIfPart() {
    doTest();
  }

  public void testConditionalRaiseReturnInElsePart() {
    doTest();
  }

  public void testConditionalRaiseNestedIfs() {
    doTest();
  }

  public void testConditionalRaiseReturnInElifPart() {
    doTest();
  }

  // PY-25624
  public void testConditionalRaiseNoReturn() {
    doTest();
  }

  // PY-26300
  public void testOverriddenAsFieldInAncestor() {
    doTest();
  }

  // PY-26628
  public void testTypingProtocolSubclass() {
    doTest();
  }

  // PY-30789
  public void testHiddenForAbstractSubclassWithABCSuperclass() {
    doMultiFileTest();
  }

  // PY-12132
  public void testInstantiateAbstractClass() {
    doTest();
  }

  // PY-12132
  public void testInstantiateAbstractClass2() {
    doTest();
  }

  // PY-12132
  public void testInstantiateAbstractClass3() {
    doTest();
  }

  // PY-12132
  public void testAbstractMethodInNonAbstractClass() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyAbstractClassInspection.class;
  }
}
