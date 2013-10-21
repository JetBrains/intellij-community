/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyAugmentAssignmentInspectionTest extends PyTestCase {

  public void testMult() {
    doTest();
  }

  public void testAdd() {
    doTest();
  }

  public void testNegativeAssignment() {
    doTest();
  }

  public void testNegativeName() {
    doTest();
  }

  public void testNegative() {
    doTest();
  }

  public void testNegativeMinus() {
    doTest();
  }

  public void testNegativeString() {
    doTest();
  }

  public void testString() {
    doTest();
  }

  public void testNumeric() {
    doTest();
  }

  public void testList() {
    doTest();
  }

  public void testDifferentOperations() {
    doTest();
  }

  // PY-7605
  public void testStrOrUnknownFirstArg() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyAugmentAssignmentInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyAugmentAssignmentInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }
}
