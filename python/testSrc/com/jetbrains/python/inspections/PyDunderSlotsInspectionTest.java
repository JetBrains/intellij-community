// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

public class PyDunderSlotsInspectionTest extends PyInspectionTestCase {

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

  // PY-26319
  public void testWriteToProperty() {
    doTestPy2();
    doTestPy3();
  }

  // PY-26319
  public void testWriteToInheritedProperty() {
    doTestPy2();
    doTestPy3();
  }

  // PY-29230
  public void testWriteToOldStyleClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON26,
      () -> doTestByText("class A:\n" +
                         "    __slots__ = ['bar']\n" +
                         "    \n" +
                         "A().baz = 1\n" +
                         "\n" +
                         "\n" +
                         "class B:\n" +
                         "    __slots__ = ['bar']\n" +
                         "    \n" +
                         "class C(B):\n" +
                         "    __slots__ = ['baz']\n" +
                         "    \n" +
                         "C().foo = 1")
    );
  }

  // PY-29234
  public void testSlotAndAnnotatedClassVar() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("class MyClass:\n" +
                         "    __slots__ = ['a']\n" +
                         "    a: int")
    );
  }

  // PY-29268
  public void testWriteToNewStyleInheritedFromOldStyle() {
    doTestByText("class A:\n" +
                 "    __slots__ = ['a']\n" +
                 "\n" +
                 "class B(A, object):\n" +
                 "    __slots__ = ['b']\n" +
                 "\n" +
                 "B().c = 1");
  }

  // PY-29268
  public void testWriteToNewStyleInheritedFromUnknown() {
    doTestByText("class B(A, object):\n" +
                 "    __slots__ = ['b']\n" +
                 "\n" +
                 "B().c = 1");
  }

  private void doTestPy2() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, this::doTest);
  }

  private void doTestPy3() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyDunderSlotsInspection.class;
  }

  @Override
  protected void doTest() {
    final String path = getTestFilePath();
    final VirtualFile file = myFixture.getTempDirFixture().getFile(path);
    if (file != null) {
      try {
        WriteAction.run(() -> file.delete(this));
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    super.doTest();
  }
}
