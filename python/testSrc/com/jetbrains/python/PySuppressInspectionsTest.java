package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.inspections.PyUnusedLocalInspection;

/**
 * @author yole
 */
public class PySuppressInspectionsTest extends PyLightFixtureTestCase {
  public void testSuppressedForStatement() throws Exception {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedForMethod() throws Exception {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedForClass() throws Exception {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedUnusedLocal() throws Exception {
    doTestHighlighting(PyUnusedLocalInspection.class);
  }

  private void doTestHighlighting(final Class<? extends PyInspection> inspectionClass) throws Exception {
    myFixture.configureByFile("inspections/suppress/" + getTestName(true) + ".py");
    myFixture.enableInspections(inspectionClass);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testSuppressForStatement() throws Exception {
    myFixture.configureByFile("inspections/suppress/suppressForStatement.py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    final IntentionAction suppressAction = myFixture.findSingleIntention("Suppress for statement");
    assertNotNull(suppressAction);
    myFixture.launchAction(suppressAction);
    myFixture.checkResultByFile("inspections/suppress/suppressForStatement.after.py");
  }
}
