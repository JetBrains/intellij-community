package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;


/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class StringWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {

  protected String getBasePath() {
    return "/plugins/spellchecker/tests/testData/inspection/stringWithMistakes";
  }


  public void testJava() throws Throwable {
    doTest("SPITest5.java", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  
}