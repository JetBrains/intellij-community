package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.java.DocCommentWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class DocCommentWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  @Override
  public String getDataPath() {
    return "/inspection/docCommentWithMistakes/data";
  }

  public void testJava() throws Exception {
    doTest(getTestName(true), new DocCommentWithMistakesInspection());
  }

}