package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.java.DocCommentWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class DocCommentWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {

   
  protected String getBasePath() {
    return "/plugins/spellchecker/core/testData/inspection/docCommentWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("SPITest6.java", new DocCommentWithMistakesInspection());
  }
}