package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.java.LocalVariableNameWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class LocalVariableWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  @Override
  public String getDataPath() {
    return "/inspection/localVariableNameWithMistakes/data";
  }

  public void testJava() throws Exception {
    doTest(getTestName(true), new LocalVariableNameWithMistakesInspection());
  }


}