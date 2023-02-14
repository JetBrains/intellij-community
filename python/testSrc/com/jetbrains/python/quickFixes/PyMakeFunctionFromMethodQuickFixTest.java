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
import com.jetbrains.python.inspections.PyMethodMayBeStaticInspection;

public class PyMakeFunctionFromMethodQuickFixTest extends PyQuickFixTestCase {

  public void testOneParam() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testTwoParams() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testEmptyParam() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testFirstMethod() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testEmptyStatementList() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testNoSelf() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testUpdateUsage() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testUsageClassCallArgument() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testUsageAssignment() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testUsageImport() {
    doMultifilesTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"), new String[]{"test.py"});
  }

  public void testUsageImport1() {
    doMultifilesTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"), new String[]{"test.py"});
  }

  public void testUsageImport2() {
    doMultifilesTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"), new String[]{"test.py"});
  }

  public void testUsageSelf() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testLocalClass() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  public void testRemoveQualifiers() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }

  // EA-425763
  public void testInnerClassMethod() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyPsiBundle.message("QFIX.NAME.make.function"));
  }
}
