// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
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
    myFixture.configureByFile("unittest.py");
    doTest();
  }

  public void testNamedTuple() {
    doTest();
  }

  public void testFromImport() {
    doTest();
  }

  public void testAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> {
      PyProtectedMemberInspection inspection = new PyProtectedMemberInspection();
      inspection.ignoreAnnotations = true;
      myFixture.enableInspections(inspection);
      final PsiFile currentFile = myFixture.configureByFile(getTestFilePath());
      myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
      assertSdkRootsNotParsed(currentFile);
    });
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
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doMultiFileTest);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyProtectedMemberInspection.class;
  }
}
