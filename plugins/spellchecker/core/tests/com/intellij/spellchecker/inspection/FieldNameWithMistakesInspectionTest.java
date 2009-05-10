package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.java.FieldNameWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class FieldNameWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {


  protected String getBasePath() {
    return "/plugins/spellchecker/core/testData/inspection/fieldNameWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("SPITest2.java", new FieldNameWithMistakesInspection());
  }

}