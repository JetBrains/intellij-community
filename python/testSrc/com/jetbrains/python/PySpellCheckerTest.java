// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;


public class PySpellCheckerTest extends PyTestCase {
  public void testPlainTextSplitter() {
    doTest();
  }

  public void testPlainTextSplitter2() {
    doTest();
  }

  public void testPlainTextSplitter3() {
    doTest();
  }

  public void testTypoAfterEscapeSequence() {  // PY-4440
    doTest();
  }

  public void testIgnoreEscapeSequence() {  // PY-6794
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  // PY-20824
  public void testFStringPrefix() {
    doTest();
  }

  public void testFStringExpression() {
    doTest();
  }

  public void testRawFString() {
    doTest();
  }

  // PY-34873
  public void testFStringsWithApostrophe() {
    doTest();
  }

  // PY-20987
  public void testEscapesInRawAndNormalGluedStringElements() {
    doTest();
  }

  // PY-20987
  public void testGluedStringNodesAfterFirstWithPrefix() {
    doTest();
  }

  public void testTyposInInjectedPythonStringsReportedOnce() {
    doTest();
  }

  // PY-36912
  public void testTyposInDoctestsReportedOnce() {
    doTest();
  }

  // PY-7711
  public void testTyposInRegexIgnored() {
    doTest();
  }

  private void doTest() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByFile("inspections/spelling/" + getTestName(true) + ".py");
    myFixture.checkHighlighting(true, false, true);
  }
}
