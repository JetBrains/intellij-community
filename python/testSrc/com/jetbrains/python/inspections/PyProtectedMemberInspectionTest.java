/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

@TestDataPath("$CONTENT_ROOT/../testData/inspections/PyProtectedMemberInspection")
public class PyProtectedMemberInspectionTest extends PyTestCase {

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
    setLanguageLevel(LanguageLevel.PYTHON34);
    PyProtectedMemberInspection inspection = new PyProtectedMemberInspection();
    inspection.ignoreAnnotations = true;
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.checkHighlighting(false, false, true);
  }

  //PY-14234
  public void testImportFromTheSamePackage() {
    String path = getTestName(true);
    myFixture.copyDirectoryToProject(path + "/my_package", "./my_package");
    myFixture.configureByFile("/my_package/my_public_module.py");
    myFixture.enableInspections(PyProtectedMemberInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  public void testModule() {
    myFixture.configureByFiles(getTestName(true) + ".py", "tmp.py");
    myFixture.enableInspections(PyProtectedMemberInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.enableInspections(PyProtectedMemberInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/inspections/PyProtectedMemberInspection/";
  }
}
