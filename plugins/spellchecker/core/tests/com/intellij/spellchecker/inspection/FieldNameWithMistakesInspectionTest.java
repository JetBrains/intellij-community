package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.java.FieldNameWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class FieldNameWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  @Override
  public String getDataPath() {
    return "/inspection/fieldNameWithMistakes/data";
  }


  public void testJava() throws Exception {
    doTest(getTestName(true), new FieldNameWithMistakesInspection());
  }


}