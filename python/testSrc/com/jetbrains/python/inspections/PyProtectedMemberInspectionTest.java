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
      setLanguageLevel(LanguageLevel.PYTHON34);
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

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyProtectedMemberInspection.class;
  }
}
