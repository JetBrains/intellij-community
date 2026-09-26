// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.idea.TestFor;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyStatementEffectInspection;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalVariableInspection;

@Subsystems.Inspections
@Layers.Functional
@TestFor(issues = "PY-45308")
public class PyRedundantSuppressionTest extends PyTestCase {
  // A function-level suppression that mutes nothing is reported and removed completely.
  public void testRedundantForFunction() {
    doFixTest("PyUnusedLocal", new RedundantSuppressInspection(), new PyUnusedLocalVariableInspection());
  }

  // A statement-level suppression that mutes nothing is reported and removed completely.
  public void testRedundantForStatement() {
    doFixTest("PyUnusedLocal", new RedundantSuppressInspection(), new PyUnusedLocalVariableInspection());
  }

  // Only the redundant id is removed; the one that still suppresses a warning is kept.
  public void testRedundantOneOfMultiple() {
    doFixTest("PyStatementEffect", new RedundantSuppressInspection(), new PyUnusedLocalVariableInspection(), new PyStatementEffectInspection());
  }

  // A suppression that still mutes a warning must not be reported as redundant.
  public void testActiveSuppressionNotReported() {
    doHighlightTest(new RedundantSuppressInspection(), new PyUnusedLocalVariableInspection());
  }

  private void doHighlightTest(InspectionProfileEntry... inspections) {
    myFixture.configureByFile("inspections/redundantSuppression/" + getTestName(true) + ".py");
    myFixture.enableInspections(inspections);
    myFixture.checkHighlighting(true, false, true);
  }

  private void doFixTest(String toolId, InspectionProfileEntry... inspections) {
    myFixture.configureByFile("inspections/redundantSuppression/" + getTestName(true) + ".py");
    myFixture.enableInspections(inspections);
    myFixture.checkHighlighting(true, false, true);
    final IntentionAction intention =
      myFixture.findSingleIntention(PyPsiBundle.message("INSP.redundant.suppression.remove.quickfix.name", toolId));
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("inspections/redundantSuppression/" + getTestName(true) + ".after.py");
  }
}
