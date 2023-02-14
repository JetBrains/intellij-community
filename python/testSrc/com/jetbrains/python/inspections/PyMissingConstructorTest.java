// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;


public class PyMissingConstructorTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyMissingConstructorInspection/";

  public void testBasic() {
    doTest();
  }

  // PY-3278
  public void testQualifiedName() {
    doTest();
  }

  // PY-3238
  public void testNoConstructor() {
    doTest();
  }

  // PY-3313
  public void testDeepInheritance() {
    doTest();
  }

  // PY-3395
  public void testInheritFromSelf() {
    doTest();
  }

  // PY-4038
  public void testExplicitDunderClass() {
    doTest();
  }

  // PY-20038
  public void testImplicitDunderClass() {
    doTest();
  }

  // PY-7176
  public void testException() {
    doTest();
  }

  // PY-7699
  public void testInnerClass() {
    doTest();
  }

  public void testPy3k() {
    doTest();
  }

  // PY-33265
  public void testAbstractConstructor() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py");
    myFixture.enableInspections(PyMissingConstructorInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
