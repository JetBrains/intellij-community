// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

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

  // PY-76811
  public void testDataclassSlotsEnabledTwice() {
    doTestByText("""
                   from dataclasses import dataclass
                   
                   @dataclass(<warning descr="A data class that explicitly defines '__slots__' must not be configured with 'slots=True'">slots=True</warning>)
                   class C:
                        __slots__ = ("a",)
                   """);
  }

  // PY-76811
  public void testDataclassSlotsEnabledTwiceQuickFix() {
    String before = """
      from dataclasses import dataclass
      
      @dataclass(<warning descr="A data class that explicitly defines '__slots__' must not be configured with 'slots=True'">slots=<caret>True</warning>)
      class C:
          __slots__ = ("a",)
      """;

    String after = """
      from dataclasses import dataclass
      
      @dataclass()
      class C:
          __slots__ = ("a",)
      """;

    myFixture.configureByText(PythonFileType.INSTANCE, before);
    configureInspection();
    var action = myFixture.findSingleIntention(PyPsiBundle.message("QFIX.dunder.slots.enabled.twice"));
    myFixture.launchAction(action);
    myFixture.checkResult(after);
  }

  // PY-76811
  public void testDataclassSlotsExcludesInitVarClassVarUnannotatedAndNonFields() {
    String code = """
      from dataclasses import dataclass, InitVar
      from typing import ClassVar
      
      @dataclass(slots=True)
      class C1:
          a: int                    # should be slotted
          b: ClassVar[int]          # excluded (ClassVar)
          c = 1                     # excluded (unannotated class attr)
          d: InitVar[int]           # excluded (InitVar)
      
          # Non-field members should never become slots
          def meth(self):           # excluded (method)
              pass
      
          @property                 # excluded (property)
          def p(self) -> int:
              return 0
      """;

    PyClass cls = loadClass("C1", code);
    TypeEvalContext ctx = TypeEvalContext.codeAnalysis(myFixture.getProject(), cls.getContainingFile());

    List<String> slots = cls.getSlots(ctx);
    assertNotNull("Slots should be computed for dataclass(slots=True)", slots);

    // Only 'a' should be present
    assertEquals(List.of("a"), slots);
  }

  // PY-76811
  public void testDataclassSlotsExcludesInitVarAndClassVarViaAliases() {
    String code = """
      import dataclasses as dc
      import typing as t
      
      @dc.dataclass(slots=True)
      class C2:
          ok: int                  # should be slotted
          cv: t.ClassVar[str]      # excluded
          iv: dc.InitVar[bytes]    # excluded
          u = object()             # excluded (unannotated)
      """;

    PyClass cls = loadClass("C2", code);
    TypeEvalContext ctx = TypeEvalContext.codeAnalysis(myFixture.getProject(), cls.getContainingFile());

    List<String> slots = cls.getSlots(ctx);
    assertNotNull("Slots should be computed for dataclass(slots=True)", slots);

    assertEquals(List.of("ok"), slots);
  }

  private @NotNull PyClass loadClass(@NotNull String className, @NotNull String code) {
    myFixture.configureByText(PythonFileType.INSTANCE, code);
    PyFile pyFile = (PyFile)myFixture.getFile();
    PyClass cls = pyFile.findTopLevelClass(className);
    assertNotNull("Class " + className + " should be found", cls);
    return cls;
  }

  public void testDataclassSlotsAttrIsWritable() {
    doTestByText("""
      from dataclasses import dataclass
      
      @dataclass(slots=True)
      class DataClassWithSlots:
          first_value: int = 0
      
      def fun(x : DataClassWithSlots) :
          x.first_value = 10
      """);
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
