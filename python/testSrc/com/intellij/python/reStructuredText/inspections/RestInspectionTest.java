// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.inspections;

import com.intellij.python.reStructuredText.fixtures.RestFixtureTestCase;

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
