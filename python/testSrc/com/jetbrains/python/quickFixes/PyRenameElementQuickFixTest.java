/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.jetbrains.python.inspections.PyCompatibilityInspection;
import com.jetbrains.python.inspections.PyPep8NamingInspection;
import com.jetbrains.python.inspections.PyProtectedMemberInspection;
import com.jetbrains.python.inspections.PyShadowingBuiltinsInspection;
import com.jetbrains.python.psi.LanguageLevel;

@TestDataPath("$CONTENT_ROOT/../testData//quickFixes/RenameElementQuickFixTest/")
public class PyRenameElementQuickFixTest extends PyQuickFixTestCase {

  public void testProtectedMember() {
    doQuickFixTest(PyProtectedMemberInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  public void testPep8() {
    doQuickFixTest(PyPep8NamingInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  public void testPep8Class() {
    doQuickFixTest(PyPep8NamingInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  public void testPep8Function() {
    doQuickFixTest(PyPep8NamingInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  public void testShadowingBuiltins() {
    doQuickFixTest(PyShadowingBuiltinsInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  // PY-16098
  public void testRenameAsyncClassInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAwaitClassInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAsyncFunctionInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAwaitFunctionInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAsyncVariableInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAwaitVariableInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }
}
