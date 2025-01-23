// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
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
    //myFixture.configureByText("test.py", """
    //  <warning descr="Unused import statement 'import unresolved1'">import <error descr="No module named 'unresolved1'">unresolved1</error></warning>
    //  <warning descr="Unused import statement 'import unresolved2'">import <error descr="No module named 'unresolved2'">unresolved2</error></warning>
    //  <warning descr="Unused import statement 'import unresolved3'">import <error descr="No module named 'unresolved3'">unresolved3</error></warning>
    //  """);
    //myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    //
    //
    //final ExpectedHighlightingData data = new ExpectedHighlightingData(myFixture.getEditor().getDocument(), true, true, false);
    //data.init();
    //
    //myFixture.type("\n");
    //myFixture.type("\b");
    //
    //((CodeInsightTestFixtureImpl)myFixture).collectAndCheckHighlighting(data);
  }

  //PY-20075
  public void testMultipleSubmodules() {
    doTest("test1.py");
  }

  private void doTest(@NotNull String filename) {
    myFixture.copyDirectoryToProject("inspections/unusedImport/" + getTestName(true), "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.testHighlighting(true, false, false, filename);
  }
}
