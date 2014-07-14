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

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyMethodMayBeStaticInspection;

/**
 * User: ktisha
 */
public class PyMakeMethodStaticQuickFixTest extends PyQuickFixTestCase {

  public void testOneParam() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testTwoParams() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testEmptyParam() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testNoSelf() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testUsage() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testUsageImport() {
    doMultifilesTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"), new String[]{"test.py"});
  }

}
