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

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

public class PyClassHasNoInitInspectionTest extends PyTestCase {

  public void testClass() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testParentClass() {
    doTest();
  }

  public void testInitInParentClass() {
    doTest();
  }

  public void testUnresolvedParent() {
    doTest();
  }

  public void testNew() {
    doTest();
  }

  public void testMeta() {
    doTest();
  }

  public void testUnresolvedAncestor() {
    doTest();
  }

  // PY-24436
  public void testAInheritsBAndBInheritsImportedAWithDunderInit() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doMultiFileTest);
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyClassHasNoInitInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyClassHasNoInitInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  private void doMultiFileTest() {
    final String folderPath = "inspections/PyClassHasNoInitInspection/" + getTestName(false) + "/";

    myFixture.copyDirectoryToProject(folderPath, "");
    myFixture.configureFromTempProjectFile("a.py");
    myFixture.enableInspections(PyClassHasNoInitInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
