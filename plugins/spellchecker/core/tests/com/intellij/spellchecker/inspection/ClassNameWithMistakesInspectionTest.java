package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.java.ClassNameWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class ClassNameWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  @Override
  public String getDataPath() {
    return "/inspection/classNameWithMistakes/data";
  }

  public void testJava() throws Exception {
    doTest(getTestName(true), new ClassNameWithMistakesInspection());
  }


}