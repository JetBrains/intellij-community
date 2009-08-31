package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class ClassNameWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  protected String getBasePath() {
    return getSpellcheckerTestDataPath() + "/inspection/classNameWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("TestUpgade.java",SpellCheckerInspectionToolProvider.getInspectionTools());
  }


}