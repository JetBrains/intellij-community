package com.jetbrains.rest.inspections;

import com.jetbrains.rest.fixtures.RestFixtureTestCase;

/**
 * User : catherine
 */
public class RestInspectionTest extends RestFixtureTestCase {
  public void testUndefinedRole() {
    doTest(RestRoleInspection.class);
  }

  public void testHyperlinkAnnotator() {
    doTest();
  }

  public void testFootnoteAnnotator() {
    doTest();
  }

  public void testReferenceTargetAnnotator() {
    doTest();
  }

  private void doTest(Class<? extends RestInspection> inspection) {
    myFixture.configureByFile("/inspections/" + getTestName(true) + ".rst");
    if (inspection != null)
      myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(true, false, false);
  }

  private void doTest() {
    doTest(null);
  }
}
