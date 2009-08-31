package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class GroovyFileWithTypos extends SpellcheckerInspectionTestCase {

  protected String getBasePath() {
    return getSpellcheckerTestDataPath() + "/inspection/groovy";
  }

  public void testGroovy() throws Throwable {
    doTest("Test.groovy",SpellCheckerInspectionToolProvider.getInspectionTools());
  }


}