package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.java.MethodNameWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class MethodNameWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  @Override
  public String getDataPath() {
    return "/inspection/methodNameWithMistakes/data";
  }

  public void testJava() throws Exception {
    doTest(getTestName(true), new MethodNameWithMistakesInspection());
  }


}