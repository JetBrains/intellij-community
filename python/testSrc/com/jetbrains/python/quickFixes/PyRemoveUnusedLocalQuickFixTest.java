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
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyRemoveUnusedLocalQuickFixTest/")
public class PyRemoveUnusedLocalQuickFixTest extends PyQuickFixTestCase {
  // PY-20893
  public void testExcept() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("QFIX.NAME.remove.exception.target"));
  }

  // PY-20893
  public void testExcept2() {
    runWithLanguageLevel(LanguageLevel.PYTHON27,
                         () -> doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("QFIX.NAME.remove.exception.target")));
  }

  // PY-26418
  public void testWithOneTarget() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("QFIX.NAME.remove.with.target"));
  }

  // PY-26418
  public void testWithTwoTargets() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("INSP.unused.locals.replace.with.wildcard"));
  }

  // PY-26418
  // TODO: PY-43505
  public void _testTwoWithItemsFirstUnused() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("QFIX.NAME.remove.with.target"));
    });
  }

  // PY-26418
  public void testTwoWithItemsSecondUnused() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("QFIX.NAME.remove.with.target"));
    });
  }

  // PY-17901
  public void testRemoveAssignmentStatementTarget() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("QFIX.NAME.remove.assignment.target"));
    });
  }

  // PY-28782
  public void testRemoveChainedAssignmentStatementFirstTarget() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("QFIX.NAME.remove.assignment.target"));
    });
  }

  // PY-28782
  public void testRemoveChainedAssignmentStatementSecondTarget() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("QFIX.NAME.remove.assignment.target"));
    });
  }

  // PY-28782
  public void testRemoveChainedAssignmentStatementUnpackingFirstTarget() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doTestNotIgnoreTupleUnpacking(PyPsiBundle.message("INSP.unused.locals.replace.with.wildcard"));
    });
  }

  // PY-28782
  public void testRemoveChainedAssignmentStatementUnpackingSecondTarget() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doTestNotIgnoreTupleUnpacking(PyPsiBundle.message("INSP.unused.locals.replace.with.wildcard"));
    });
  }

  private void doTestNotIgnoreTupleUnpacking(@NotNull String hint) {
    final String testFileName = getTestName(true);
    final PyUnusedLocalInspection inspection = new PyUnusedLocalInspection();
    inspection.ignoreTupleUnpacking = false;
    myFixture.configureByFile(testFileName + ".py");
    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.py", true);
  }

  // PY-32037
  public void testGeneratorIterator() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("INSP.unused.locals.replace.with.wildcard"));
  }

  // PY-32037
  public void testComprehensionIterator() {
    doQuickFixTest(PyUnusedLocalInspection.class, PyPsiBundle.message("INSP.unused.locals.replace.with.wildcard"));
  }
}
