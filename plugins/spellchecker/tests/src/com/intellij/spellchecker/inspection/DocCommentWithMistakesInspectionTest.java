package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class DocCommentWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {

   
  protected String getBasePath() {
    return "/plugins/spellchecker/tests/testData/inspection/docCommentWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("SPITest6.java", SpellCheckerInspectionToolProvider.getInspectionTools());
  }
}