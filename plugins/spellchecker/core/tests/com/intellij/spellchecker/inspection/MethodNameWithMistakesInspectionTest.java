package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.java.MethodNameWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class MethodNameWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {

   protected String getBasePath() {
    return "/plugins/spellchecker/core/testData/inspection/methodNameWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("SPITest4.java", new MethodNameWithMistakesInspection());
  }

}