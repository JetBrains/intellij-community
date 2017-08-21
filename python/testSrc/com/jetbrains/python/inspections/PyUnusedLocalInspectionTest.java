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
import org.jetbrains.annotations.NotNull;

public class PyUnusedLocalInspectionTest extends PyTestCase {

  public void testPy2() {
    final PyUnusedLocalInspection inspection = new PyUnusedLocalInspection();
    inspection.ignoreTupleUnpacking = false;
    inspection.ignoreLambdaParameters = false;
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doTest(inspection));
  }

  public void testNonlocal() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }

  // PY-1235
  public void testTupleUnpacking() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, this::doTest);
  }

  // PY-959
  public void testUnusedFunction() {
    doTest();
  }

  // PY-9778
  public void testUnusedCoroutine() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doMultiFileTest);
  }

  // PY-19491
  public void testArgsAndKwargsInDunderInit() {
    doTest();
  }

  // PY-20805
  public void testFStringReferences() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-22087
  public void testFStringReferencesInComprehensions() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-8219
  public void testDoctestReference() {
    doTest();
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-23057
  public void testParameterInMethodWithEllipsis() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testSingleUnderscore() {
    doTest();
  }

  // PY-3996
  public void testUnderscorePrefixed() {
    doTest();
  }

  // PY-20655
  public void testCallingLocalsLeadsToUnusedParameter() {
    doTest();
  }

  private void doTest() {
    final String path = "inspections/PyUnusedLocalInspection/" + getTestName(true) + ".py";

    myFixture.configureByFile(path);
    myFixture.enableInspections(PyUnusedLocalInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  private void doTest(@NotNull PyUnusedLocalInspection inspection) {
    final String path = "inspections/PyUnusedLocalInspection/" + getTestName(true) + ".py";

    myFixture.configureByFile(path);
    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(true, false, true);
  }

  private void doMultiFileTest() {
    final String folderPath = "inspections/PyUnusedLocalInspection/" + getTestName(false) + "/";

    myFixture.copyDirectoryToProject(folderPath, "");
    myFixture.configureFromTempProjectFile("b.py");
    myFixture.enableInspections(PyUnusedLocalInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }
}
