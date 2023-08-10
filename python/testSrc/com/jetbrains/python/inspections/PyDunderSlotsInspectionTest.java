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
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndSlotsWithDict() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndSlotsWithAttrPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndSlotsWithAttrPy3() {
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndInheritedSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndInheritedSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnSlotsAndEmptyParent() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithDictAndInheritedSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithDictAndInheritedWithAttrSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedWithDictSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithAttrAndInheritedSlotsPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnWithAttrAndInheritedSlotsPy3() {
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedWithAttrSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testClassAttrAssignmentAndOwnAndInheritedWithAttrAndDictSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithAttrAndInheritedSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithDictAndInheritedSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithAttrAndDictAndInheritedSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithAttrSlotsPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithAttrSlotsPy3() {
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithDictSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithAttrAndDictSlotsPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnAndInheritedWithAttrAndDictSlotsPy3() {
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithAttrAndInheritedWithDictSlots() {
    doTestPy2();
    doTest();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithDictAndInheritedWithAttrSlotsPy2() {
    doTestPy2();
  }

  // PY-12773
  public void testInheritedClassAttrAssignmentAndOwnWithDictAndInheritedWithAttrSlotsPy3() {
    doTest();
  }

  // PY-19956
  public void testWriteToAttrInSlots() {
    doTestPy2();
    doTest();
  }

  // PY-20280
  public void testSlotInListAndClassVar() {
    doTest();
  }

  // PY-20280
  public void testSlotInTupleAndClassVar() {
    doTest();
  }

  // PY-20280
  public void testSlotAndClassVar() {
    doTest();
  }

  // PY-20280
  public void testTwoSlotsInListAndClassVar() {
    doTest();
  }

  // PY-20280
  public void testTwoSlotsInTupleAndClassVar() {
    doTest();
  }

  // PY-20280
  public void testSlotsInBaseAndClassVarInDerived() {
    doTest();
  }

  // PY-20280
  public void testClassVarInBaseAndSlotsInDerived() {
    doTest();
  }

  // PY-20280
  public void testSlotsInBaseAndDerivedClassVarInDerived() {
    doTest();
  }

  // PY-20280
  public void testSlotsInBaseAndDerivedClassVarInBase() {
    doTest();
  }

  // PY-22716
  public void testWriteToInheritedSlot() {
    doTestPy2();
    doTest();
  }

  // PY-26319
  public void testWriteToProperty() {
    doTestPy2();
    doTest();
  }

  // PY-26319
  public void testWriteToInheritedProperty() {
    doTestPy2();
    doTest();
  }

  // PY-29230
  public void testWriteToOldStyleClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON27,
      () -> doTestByText("""
                           class A:
                               __slots__ = ['bar']
                              \s
                           A().baz = 1


                           class B:
                               __slots__ = ['bar']
                              \s
                           class C(B):
                               __slots__ = ['baz']
                              \s
                           C().foo = 1""")
    );
  }

  // PY-29234
  public void testSlotAndAnnotatedClassVar() {
    doTestByText("""
                   class MyClass:
                       __slots__ = ['a']
                       a: int""");
  }

  // PY-29268
  public void testWriteToNewStyleInheritedFromOldStyle() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      doTestByText("""
                     class A:
                         __slots__ = ['a']

                     class B(A, object):
                         __slots__ = ['b']

                     B().c = 1""");
    });
  }

  // PY-29268
  public void testWriteToNewStyleInheritedFromUnknown() {
    doTestByText("""
                   class B(A, object):
                       __slots__ = ['b']

                   B().c = 1""");
  }

  // PY-31066
  public void testWriteToSlotAndAnnotatedClassVar() {
    doTestByText("""
                   class A:
                       __slots__ = ['a']
                       a: int

                       def __init__(self, a: int) -> None:
                           self.a = a""");
  }

  private void doTestPy2() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
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
