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

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class Py3UnresolvedReferencesInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnresolvedReferencesInspection3K/";

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  private void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py");
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        myFixture.checkHighlighting(true, false, false);
      }
    });
  }

  private void doMultiFileTest(@NotNull final String filename) {
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        final String testName = getTestName(false);
        myFixture.copyDirectoryToProject(TEST_DIRECTORY + testName, "");
        myFixture.configureFromTempProjectFile(filename);
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        myFixture.checkHighlighting(true, false, false);
      }
    });
  }

  public void testNamedTuple() {
    doTest();
  }

  public void testNamedTupleAssignment() {
    doMultiFileTest("a.py");
  }

  // TODO: Currently there are no stubs for namedtuple() in the base classes list and no indicators for forcing stub->AST
  public void _testNamedTupleBaseStub() {
    doMultiFileTest("a.py");
  }

  // PY-10208
  public void testMetaclassMethods() {
    doTest();
  }

  // PY-9011
  public void testDatetimeDateAttributesOutsideClass() {
    doMultiFileTest("a.py");
  }

  public void testObjectNewAttributes() {
    doTest();
  }
}
