package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class CommentsWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {


  protected String getBasePath() {
    return getSpellcheckerTestDataPath() + "/inspection/commentsWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("SPITest1.java", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  public void testXml() throws Throwable {
    doTest("A.xml",SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  public void testHtml() throws Throwable {
    doTest("test.html", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  public void testTxt() throws Throwable {
    doTest("test.txt", SpellCheckerInspectionToolProvider.getInspectionTools());
  }
}
