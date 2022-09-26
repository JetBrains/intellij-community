// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.spellchecker;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ShSpellcheckerTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/core/testData/spellchecker";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SpellCheckingInspection());
  }

  public void testTypoInString() { doTest(); }
  public void testTypoInHereDoc() { doTest(); }
  public void testTypoInComment() { doTest(); }
  public void testTypoInRowString() { doTest(); }
  public void testTypoInVariableName() { doTest(); }

  private void doTest() {
    myFixture.testHighlighting(false, false, true, getTestName(true) + ".sh");
  }
}