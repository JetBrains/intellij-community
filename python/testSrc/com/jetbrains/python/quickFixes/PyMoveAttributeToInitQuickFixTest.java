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
package com.jetbrains.python.quickFixes;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyAttributeOutsideInitInspection;
import com.jetbrains.python.psi.LanguageLevel;

@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyMoveAttributeToInitQuickFixTest")
public class PyMoveAttributeToInitQuickFixTest extends PyQuickFixTestCase {

  public void testMoveToInit() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testCreateInit() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testAddPass() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testRemovePass() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testSkipDocstring() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testAddSuperCall() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testAddSuperCallOldStyle() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testPropertyNegative() {
    doInspectionTest(PyAttributeOutsideInitInspection.class);
  }

  public void testPy3K() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"), LanguageLevel.PYTHON34);
  }

}
