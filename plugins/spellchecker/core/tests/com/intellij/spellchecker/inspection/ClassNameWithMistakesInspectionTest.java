package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.java.ClassNameWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class ClassNameWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {

  protected String getBasePath() {
    return "/plugins/spellchecker/core/testData/inspection/classNameWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("TestUpgade.java", new ClassNameWithMistakesInspection());
  }


}