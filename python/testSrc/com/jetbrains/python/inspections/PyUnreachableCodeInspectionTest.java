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

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author vlan
 */
public class PyUnreachableCodeInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnreachableCodeInspection/";

  // All previous unreachable tests, feel free to split them
  public void testUnreachable() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  // PY-7420
  public void testWithSuppressedExceptions() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(false) + ".py");
    myFixture.enableInspections(PyUnreachableCodeInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
