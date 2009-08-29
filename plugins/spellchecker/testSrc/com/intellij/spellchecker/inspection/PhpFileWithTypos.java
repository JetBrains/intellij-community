package com.intellij.spellchecker.inspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class PhpFileWithTypos extends SpellcheckerInspectionTestCase {

  protected String getBasePath() {
    return "/plugins/spellchecker/tests/testData/inspection/php";
  }

  public void testPhp() throws Throwable {
    //doTest("test.php",SpellCheckerInspectionToolProvider.getInspectionTools());
  }


}