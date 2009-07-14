package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class JSFileWithTypos extends SpellcheckerInspectionTestCase {

  protected String getBasePath() {
    return "/plugins/spellchecker/tests/testData/inspection/js";
  }

  public void testJS() throws Throwable {
    doTest("test.js",SpellCheckerInspectionToolProvider.getInspectionTools());
  }


}