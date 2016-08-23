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

/**
 * @author vlan
 */
public class Py3TypeCheckerInspectionTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "inspections/PyTypeCheckerInspection/";

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  private void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> {
      myFixture.copyDirectoryToProject("typing", "");
      myFixture.configureByFile(TEST_DIRECTORY + getTestName(false) + ".py");
      myFixture.enableInspections(PyTypeCheckerInspection.class);
      myFixture.checkHighlighting(true, false, true);
    });
  }

  private void doMultiFileTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> {
      myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
      myFixture.copyDirectoryToProject("typing", "");
      myFixture.configureFromTempProjectFile("a.py");
      myFixture.enableInspections(PyTypeCheckerInspection.class);
      myFixture.checkHighlighting(true, false, true);
    });
  }

  // PY-9289
  public void testWithOpenBinaryPy3() {
    doTest();
  }

  // PY-10660
  public void testStructUnpackPy3() {
    doMultiFileTest();
  }

  public void testBuiltinsPy3() {
    doTest();
  }

  // PY-16125
  public void testTypingIterableForLoop() {
    doTest();
  }

  // PY-16146
  public void testTypingListSubscriptionExpression() {
    doTest();
  }

  // PY-16855
  public void testTypingTypeVarWithUnresolvedBound() {
    doTest();
  }

  // PY-16303
  public void testTypingTupleInDocstring() {
    doTest();
  }

  // PY-16898
  public void testAsyncForIterable() {
    doTest();
  }

  // PY-18275
  public void testStrFormatPy3() {
    doTest();
  }
  
  // PY-18762
  public void testHomogeneousTuples() {
    myFixture.copyDirectoryToProject("typing/typing.py", TEST_DIRECTORY);
    doTest();
  }

  // PY-9924
  public void testTupleGetItemWithSlice() {
    doTest();
  }

  // PY-9924
  public void testListGetItemWithSlice() {
    doTest();
  }

  // PY-19796
  public void testOrd() {
    doTest();
  }

  // PY-12944
  public void testDelegatedGenerator() {
    doTest();
  }

  // PY-16055
  public void testFunctionReturnTypePy3() {
    doTest();
  }
}
