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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyProtectedMemberInspection;

@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyAddPropertyForFieldQuickFixTest")
public class PyAddPropertyForFieldQuickFixTest extends PyQuickFixTestCase {
  public void testProtectedMember() {
    doQuickFixTest(PyProtectedMemberInspection.class, PyPsiBundle.message("QFIX.add.property"));
  }

  public void testUseProperty() {
    doQuickFixTest(PyProtectedMemberInspection.class, PyPsiBundle.message("QFIX.use.property"));
  }

  public void testProtectedFunction() {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(PyProtectedMemberInspection.class);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.getAvailableIntention(PyPsiBundle.message("QFIX.add.property"));
    assertNull(intentionAction);
  }
}
