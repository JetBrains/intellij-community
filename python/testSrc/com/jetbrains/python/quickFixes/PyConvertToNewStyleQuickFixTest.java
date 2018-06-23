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

import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyOldStyleClassesInspection;

@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyConvertToNewStyleQuickFixTest/")
public class PyConvertToNewStyleQuickFixTest extends PyQuickFixTestCase {

  public void testEmptySuperClassList() {
    PsiTestUtil.disablePsiTextConsistencyChecks(getTestRootDisposable());
    doQuickFixTest(PyOldStyleClassesInspection.class, PyBundle.message("QFIX.convert.to.new.style"));
  }

  public void testSlots() {
    doQuickFixTest(PyOldStyleClassesInspection.class, PyBundle.message("QFIX.convert.to.new.style"));
  }

}
