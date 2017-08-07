/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.io.IOException;
import java.io.UncheckedIOException;

public class PyDunderSlotsInspectionTest extends PyTestCase {

  // PY-12773
  public void testClassAttrAssignmentAndSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testClassAttrAssignmentAndSlotsWithDict() {
    doTestPy2();
    doTestPy3();
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
    doTestPy3();
  }

  // PY-12773
  public void testClassAttrAssignmentAndInheritedSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnSlotsAndEmptyParent() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithDictAndInheritedSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithDictAndInheritedWithAttrSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedWithDictSlots() {
    doTestPy2();
    doTestPy3();
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
    doTestPy3();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedWithAttrAndDictSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithAttrAndInheritedSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithDictAndInheritedSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithAttrAndDictAndInheritedSlots() {
    doTestPy2();
    doTestPy3();
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
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithAttrAndDictSlotsPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithAttrAndDictSlotsPy3() {
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithAttrAndInheritedWithDictSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithDictAndInheritedWithAttrSlotsPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithDictAndInheritedWithAttrSlotsPy3() {
    doTestPy3();
  }

  // PY-19956
  public void testWriteToAttrInSlots() {
    doTestPy2();
    doTestPy3();
  }

  // PY-20280
  public void testSlotInListAndClassVar() {
    doTestPy3();
  }

  // PY-20280
  public void testSlotInTupleAndClassVar() {
    doTestPy3();
  }

  // PY-20280
  public void testSlotAndClassVar() {
    doTestPy3();
  }

  // PY-20280
  public void testTwoSlotsInListAndClassVar() {
    doTestPy3();
  }

  // PY-20280
  public void testTwoSlotsInTupleAndClassVar() {
    doTestPy3();
  }

  // PY-20280
  public void testSlotsInBaseAndClassVarInDerived() {
    doTestPy3();
  }

  // PY-20280
  public void testClassVarInBaseAndSlotsInDerived() {
    doTestPy3();
  }

  // PY-20280
  public void testSlotsInBaseAndDerivedClassVarInDerived() {
    doTestPy3();
  }

  // PY-20280
  public void testSlotsInBaseAndDerivedClassVarInBase() {
    doTestPy3();
  }

  // PY-22716
  public void testWriteToInheritedSlot() {
    doTestPy2();
    doTestPy3();
  }

  private void doTestPy2() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, this::doTestPy);
  }

  private void doTestPy3() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTestPy);
  }

  private void doTestPy() {
    final String path = "inspections/PyDunderSlotsInspection/" + getTestName(true) + ".py";

    final VirtualFile file = myFixture.getTempDirFixture().getFile(path);
    if (file != null) {
      try {
        WriteAction.run(() -> file.delete(this));
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    myFixture.configureByFile(path);
    myFixture.enableInspections(PyDunderSlotsInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
