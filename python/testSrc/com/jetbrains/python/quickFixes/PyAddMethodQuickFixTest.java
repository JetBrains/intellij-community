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

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyClassHasNoInitInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;

public class PyAddMethodQuickFixTest extends PyQuickFixTestCase {

  public void testAddInit() {
    doQuickFixTest(PyClassHasNoInitInspection.class,
                   PyPsiBundle.message("QFIX.add.method.to.class", "__init__", "A"),
                   LanguageLevel.PYTHON27);
  }

  public void testAddInitAfterDocstring() {
    doQuickFixTest(PyClassHasNoInitInspection.class,
                   PyPsiBundle.message("QFIX.add.method.to.class", "__init__", "A"),
                   LanguageLevel.PYTHON27);
  }

  public void testAddMethodReplacePass() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.add.method.to.class", "y", "A"));
  }

  public void testAddMethodFromInstance() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.add.method.to.class", "y", "A"));
  }

  // PY-53120
  public void testAddAsyncMethodFromInstance() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.add.method.to.class", "y", "A"));
  }

  public void testAddMethodFromMethod() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.add.method.to.class", "y", "A"));
  }

  // PY-53120
  public void testAddAsyncMethodFromMethod() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.add.method.to.class", "y", "A"));
  }
}
