package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.common.CommentsWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class CommentsWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {


  protected String getBasePath() {
    return "/plugins/spellchecker/core/testData/inspection/commentsWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("SPITest1.java", new CommentsWithMistakesInspection());
  }

  public void testXml() throws Throwable {
    doTest("A.xml", new CommentsWithMistakesInspection());
  }

  public void testHtml() throws Throwable {
    doTest("test.html", new CommentsWithMistakesInspection());
  }

  public void testTxt() throws Throwable {
    doTest("test.txt", new CommentsWithMistakesInspection());
  }
}
