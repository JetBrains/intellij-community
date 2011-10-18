package com.jetbrains.python;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PySpellCheckerTest extends PyTestCase {
  public void testPlainTextSplitter() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByFile("inspections/spelling/plainTextSplitter.py");
    myFixture.checkHighlighting();
  }
}
