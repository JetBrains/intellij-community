package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.common.CommentsWithMistakesInspection;
import com.intellij.spellchecker.inspections.common.XmlWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class CommentsWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  @Override
  public String getDataPath() {
    return "/inspection/commentsWithMistakes/data";
  }

  public void testJava() throws Exception {
    doTest(getTestName(true), new CommentsWithMistakesInspection());
  }

  public void testXml() throws Exception {
    doTest(getTestName(true), new CommentsWithMistakesInspection());
  }

  //todo:move to another Test
  public void testHtml() throws Exception {
    doTest(getTestName(true), new XmlWithMistakesInspection());
  }

}
