package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.inspections.PyUnusedLocalInspection;

/**
 * @author yole
 */
public class PySuppressInspectionsTest extends PyTestCase {
  public void testSuppressedForStatement() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedForMethod() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedForClass() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedUnusedLocal() {
    doTestHighlighting(PyUnusedLocalInspection.class);
  }

  public void testSuppressForImport() {  // PY-2240
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  private void doTestHighlighting(final Class<? extends PyInspection> inspectionClass) {
    myFixture.configureByFile("inspections/suppress/" + getTestName(true) + ".py");
    myFixture.enableInspections(inspectionClass);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testSuppressForStatement() {
    myFixture.configureByFile("inspections/suppress/suppressForStatement.py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    final IntentionAction suppressAction = myFixture.findSingleIntention("Suppress for statement");
    assertNotNull(suppressAction);
    myFixture.launchAction(suppressAction);
    myFixture.checkResultByFile("inspections/suppress/suppressForStatement.after.py");
  }
}
