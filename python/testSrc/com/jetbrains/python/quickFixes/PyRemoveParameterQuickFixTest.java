/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.inspections.PyUnusedLocalInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

@TestDataPath("$CONTENT_ROOT/../testData//quickFixes/PyRemoveParameterQuickFixTest/")
public class PyRemoveParameterQuickFixTest extends PyQuickFixTestCase {

  public void testParam() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyBundle.message("QFIX.NAME.remove.parameter"));
  }

  public void testKwParam() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyBundle.message("QFIX.NAME.remove.parameter"));
  }

  public void testDocstring() {
    runWithDocStringFormat(DocStringFormat.REST, () ->
      doQuickFixTest(PyUnusedLocalInspection.class, PyBundle.message("QFIX.NAME.remove.parameter"))
    );
  }

  public void testUsage() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyBundle.message("QFIX.NAME.remove.parameter"));
  }

  public void testSingleStarTwoParam() {
    runWithDocStringFormat(DocStringFormat.REST, () ->
      doQuickFixTest(PyUnusedLocalInspection.class, PyBundle.message("QFIX.NAME.remove.parameter"), LanguageLevel.PYTHON33)
    );
  }

  public void testSingleStar() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON33);
    try {
      final String testFileName = getTestName(true);
      myFixture.enableInspections(PyUnusedLocalInspection.class);
      myFixture.configureByFile(testFileName + ".py");
      myFixture.checkHighlighting(true, false, false);
      final IntentionAction intentionAction = myFixture.getAvailableIntention(PyBundle.message("QFIX.NAME.remove.parameter"));
      assertNull(intentionAction);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyBundle.message("QFIX.NAME.remove.parameter"), LanguageLevel.PYTHON35);
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyBundle.message("QFIX.NAME.remove.parameter"), LanguageLevel.PYTHON35);
  }
}
