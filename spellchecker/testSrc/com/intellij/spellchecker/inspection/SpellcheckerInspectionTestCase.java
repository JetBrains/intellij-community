// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public abstract class SpellcheckerInspectionTestCase extends BasePlatformTestCase {
  @Override
  protected boolean isCommunity() {
    return true;
  }

  protected static String getSpellcheckerTestDataPath() {
    return "/spellchecker/testData/";
  }

  protected void doTest(String file) {
    myFixture.enableInspections(getInspectionTools());
    myFixture.testHighlighting(false, false, true, file);
  }

  public static LocalInspectionTool[] getInspectionTools() {
    return new LocalInspectionTool[]{new SpellCheckingInspection()};
  }
}
