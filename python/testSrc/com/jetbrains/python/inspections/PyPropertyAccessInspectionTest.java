/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyPropertyAccessInspectionTest extends PyTestCase {
  public void testTest() {
    doTestPy2();
  }

  // PY-2313
  public void testOverrideAssignment() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndSlotsWithDict() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndSlotsWithAttrPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndSlotsWithAttrPy3() {
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndInheritedSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndInheritedSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnSlotsAndEmptyParent() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithDictAndInheritedSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithDictAndInheritedWithAttrSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedWithDictSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithAttrAndInheritedSlotsPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithAttrAndInheritedSlotsPy3() {
    doTestPy3();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedWithAttrSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedWithAttrAndDictSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithAttrAndInheritedSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithDictAndInheritedSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithAttrAndDictAndInheritedSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithAttrSlotsPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithAttrSlotsPy3() {
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithDictSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithAttrAndDictSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithAttrAndInheritedWithDictSlots() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithDictAndInheritedWithAttrSlots() {
    doTestPy2();
  }

  private void doTestPy2() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, this::doTestPy);
  }

  private void doTestPy3() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTestPy);
  }

  private void doTestPy() {
    myFixture.configureByFile("inspections/PyPropertyAccessInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyPropertyAccessInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
