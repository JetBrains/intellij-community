/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * @author vlan
 */
public class PyPackageRequirementsInspectionTest extends PyTestCase {
  public void testPartiallySatisfiedRequirementsTxt() {
    doTest("test1.py");
  }

  public void testPartiallySatisfiedSetupPy() {
    doTest("test1.py");
  }

  public void testImportsNotInRequirementsTxt() {
    doTest("test1.py");
  }

  public void testDuplicateInstallAndTests() {
    doTest("test1.py");
  }

  // PY-16753
  public void testIpAddressNotInRequirements() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> doTest("test1.py"));
  }

  // PY-17422
  public void testTypingNotInRequirements() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest("test1.py"));
  }

  private void doTest(@NotNull final String filename) {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject("inspections/PyPackageRequirementsInspection/" + testName, "");
    myFixture.configureFromTempProjectFile(filename);
    myFixture.enableInspections(PyPackageRequirementsInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }
}
