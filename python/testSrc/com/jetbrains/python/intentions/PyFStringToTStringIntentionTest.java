// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * Tests for PyFStringToTStringIntention
 */
public class PyFStringToTStringIntentionTest extends PyIntentionTestCase {
  public void testSimpleFString() {
    doFStringToTStringTest();
  }

  public void testMultilineFString() {
    doFStringToTStringTest();
  }

  public void testFStringInsideTString() {
    doFStringToTStringTest();
  }

  public void testSimpleTString() {
    doTStringToFStringTest();
  }

  public void testNotAvailableForRegularString() {
    doNegativeTest();
  }

  public void testNotAvailableForDocstring() {
    doNegativeTest();
  }

  public void testRawFString() {
    doFStringToTStringTest();
  }

  public void testUppercaseFStringPrefix() {
    doFStringToTStringTest();
  }

  public void testNotAvailableBefore314() {
    runWithLanguageLevel(LanguageLevel.PYTHON310, this::doNegativeTest);
  }

  private void doFStringToTStringTest() {
    doTest(PyPsiBundle.message("INTN.convert.f.string.to.t.string"), LanguageLevel.getLatest());
  }

  private void doTStringToFStringTest() {
    doTest(PyPsiBundle.message("INTN.convert.t.string.to.f.string"), LanguageLevel.getLatest());
  }

  private void doNegativeTest() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.f.string.to.t.string"));
  }
}
