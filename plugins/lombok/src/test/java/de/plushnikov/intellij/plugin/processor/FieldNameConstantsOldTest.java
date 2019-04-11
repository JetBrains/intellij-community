package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for @FieldNameConstants annotation from old version of lombok
 */
public class FieldNameConstantsOldTest extends AbstractLombokParsingTestCase {

  private static final String OLD_LOMBOK_SRC_PATH = "./old";

  public void testFieldnameconstants$FieldNameConstantsOldBasic() {
    super.loadFilesFrom(OLD_LOMBOK_SRC_PATH);
    doTest(true);
  }

}
