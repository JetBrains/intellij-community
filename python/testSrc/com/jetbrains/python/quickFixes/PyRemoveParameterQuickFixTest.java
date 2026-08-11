// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.quickFixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedParameterInspection;
import com.jetbrains.python.psi.LanguageLevel;

@TestDataPath("$CONTENT_ROOT/../testData//quickFixes/PyRemoveParameterQuickFixTest/")
@Subsystems.QuickFixes
@Layers.Functional
public class PyRemoveParameterQuickFixTest extends PyQuickFixTestCase {

  public void testParam() {
    doQuickFixTest(PyUnusedParameterInspection.class, PyPsiBundle.message("QFIX.NAME.remove.parameter"));
  }

  public void testKwParam() {
    doQuickFixTest(PyUnusedParameterInspection.class, PyPsiBundle.message("QFIX.NAME.remove.parameter"));
  }

  public void testDocstring() {
    runWithDocStringFormat(DocStringFormat.REST, () ->
      doQuickFixTest(PyUnusedParameterInspection.class, PyPsiBundle.message("QFIX.NAME.remove.parameter"))
    );
  }

  public void testUsage() {
    doQuickFixTest(PyUnusedParameterInspection.class, PyPsiBundle.message("QFIX.NAME.remove.parameter"));
  }

  public void testSingleStarTwoParam() {
    runWithDocStringFormat(DocStringFormat.REST, () ->
      doQuickFixTest(PyUnusedParameterInspection.class, PyPsiBundle.message("QFIX.NAME.remove.parameter"), LanguageLevel.PYTHON34)
    );
  }

  public void testSingleStar() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> {
        final String testFileName = getTestName(true);
        myFixture.enableInspections(PyUnusedParameterInspection.class);
        myFixture.configureByFile(testFileName + ".py");
        myFixture.checkHighlighting(true, false, false);
        final IntentionAction intentionAction = myFixture.getAvailableIntention(PyPsiBundle.message("QFIX.NAME.remove.parameter"));
        assertNull(intentionAction);
      }
    );
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    doQuickFixTest(PyUnusedParameterInspection.class, PyPsiBundle.message("QFIX.NAME.remove.parameter"), LanguageLevel.PYTHON35);
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    doQuickFixTest(PyUnusedParameterInspection.class, PyPsiBundle.message("QFIX.NAME.remove.parameter"), LanguageLevel.PYTHON35);
  }
}
