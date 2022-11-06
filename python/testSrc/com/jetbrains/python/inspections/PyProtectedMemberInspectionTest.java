// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PyProtectedMemberInspectionTest extends PyInspectionTestCase {

  public void testTruePositive() {
    doTest();
  }

  public void testTruePositiveInClass() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testDoubleUnderscore() {
    doTest();
  }

  public void testOuterClass() {
    doTest();
  }

  public void testSelfField() {
    doTest();
  }

  public void testTest() {
    myFixture.configureByFile("packages/unittest/unittest.py");
    doTest();
  }

  public void testNamedTuple() {
    doTest();
  }

  public void testFromImport() {
    doTest();
  }

  public void testAnnotation() {
    PyProtectedMemberInspection inspection = new PyProtectedMemberInspection();
    inspection.ignoreAnnotations = true;
    myFixture.enableInspections(inspection);
    final PsiFile currentFile = myFixture.configureByFile(getTestFilePath());
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
    assertSdkRootsNotParsed(currentFile);
  }

  //PY-14234
  public void testImportFromTheSamePackage() {
    doMultiFileTest("my_package/my_public_module.py");
  }

  public void testModule() {
    doMultiFileTest();
  }

  // PY-14056
  public void testDunderAll() {
    doMultiFileTest();
  }

  public void testClassInAnotherModule() {
    doMultiFileTest();
  }

  // PY-26112
  public void testMemberResolvedToStub() {
    doMultiFileTest();
  }

  // PY-27148
  public void testTypingNamedTuple() {
    doTest();
  }

  // PY-26139
  public void testProtectedModuleInSamePackage() {
    doMultiFileTest("my_package/module2.py");
  }

  // PY-26139
  public void testProtectedModuleInPackageAbove() {
    doMultiFileTest("my_package/my_subpackage/module2.py");
  }

  // PY-32485
  public void testProtectedMemberOfSameFileClass() {
    // created file should be considered as located inside a package so Python 3 is used here
    doTestByText("""
                   class A:
                       def __init__(self, arg):
                           self._arg = arg

                       def _f(self):
                           return self._arg

                   a = A(1)
                   print(<weak_warning descr="Access to a protected member _arg of a class">a._arg</weak_warning>)
                   print(<weak_warning descr="Access to a protected member _f of a class">a._f</weak_warning>())""");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyProtectedMemberInspection.class;
  }
}
