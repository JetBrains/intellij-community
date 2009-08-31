package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class MethodNameWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {

   protected String getBasePath() {
    return getSpellcheckerTestDataPath() + "/inspection/methodNameWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("SPITest4.java", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

}