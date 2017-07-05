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

public class PyAttributeOutsideInitInspectionTest extends PyTestCase {

  public void testTruePositive() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testDefinedInSuperClass() {
    doTest();
  }

  public void testTestClass() {
    doTest();
  }

  public void testUnitTest() {
    doTest();
  }

  public void testFromSuperClassWithoutInit() {
    doTest();
  }

  public void testFromSuperHierarchy() {
    doTest();
  }

  public void testClassAttrs() {
    doTest();
  }

  public void testBaseClassAttrs() {
    doTest();
  }

  public void testInnerClass() {
    doTest();
  }

  public void testStaticMethod() {
    doTest();
  }

  public void testPrivateMethod() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("unittest.py");
    myFixture.configureByFile("inspections/PyAttributeOutsideInitInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyAttributeOutsideInitInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
