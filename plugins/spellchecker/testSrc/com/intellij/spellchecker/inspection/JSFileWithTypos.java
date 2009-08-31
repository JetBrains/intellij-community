package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class JSFileWithTypos extends SpellcheckerInspectionTestCase {

  protected String getBasePath() {
    return getSpellcheckerTestDataPath() + "/inspection/js";
  }

  public void testJS() throws Throwable {
    doTest("test.js",SpellCheckerInspectionToolProvider.getInspectionTools());
  }


}