// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;


public class PyUnusedImportTest extends PyTestCase {
  // PY-3626
  public void testModuleAndSubmodule() {
    doTest("py3626.py");
  }

  // PY-3201
  public void testSubpackageInInitPy() {
    doTest("package1/__init__.py");
  }

  // PY-5589
  public void testUnusedPackageAndSubmodule() {
    doTest("test1.py");
  }

  // PY-5621
  public void testUnusedSubmodule() {
    doTest("test1.py");
  }

  // PY-6380
  public void testUnusedAfterStarImport() {
    doTest("test1.py");
  }

  // PY-10667
  public void testUsedLastImport() {
    doTest("test1.py");
  }

  public void testUnresolvedModule() {
    doTest("test.py");
  }

  //PY-20075
  public void testMultipleSubmodules() {
    doTest("test1.py");
  }

  // PY-6955
  public void testUnusedUnresolvedPackageImported() {
    doTest();
  }

  // PY-11472
  public void testUnusedImportBeforeStarImport() {
    doTest("a.py");
  }

  public void testFromImportToContainingFile2() {  // PY-5945
    doTest("p1/m1.py");
  }

  // PY-2668
  public void testUnusedImportsInPackage() {
    doTest("p1/__init__.py");
  }

  public void testConditionalImports() { // PY-983
    doTest("a.py");
  }

  // PY-13585
  public void testUnusedImportBeforeStarDunderAll() {
    doTest("a.py");
  }

  public void testDunderAll() {
    doTest("a.py");
  }

  public void testImportExceptImportError() {
    doTest();
  }

  // PY-22620
  public void testTupleTypeCommentsUseImportsFromTyping() {
    doTest();
  }

  // PY-18521
  public void testFunctionTypeCommentUsesImportsFromTyping() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  private void doTest() {
    doTest(getTestName(true) + ".py");
  }

  private void doTest(@NotNull String filename) {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureFromTempProjectFile(filename);
    myFixture.enableInspections(PyUnusedImportsInspection.class, PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/inspections/unusedImport/";
  }
}
