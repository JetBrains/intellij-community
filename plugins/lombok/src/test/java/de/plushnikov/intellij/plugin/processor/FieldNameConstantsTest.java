package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.util.RecursionManager;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for @FieldNameConstants annotation from current version of lombok
 */
public class FieldNameConstantsTest extends AbstractLombokParsingTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testFieldnameconstants$FieldNameConstantsBasic() {
    doTest(true);
  }

  public void testFieldnameconstants$FieldNameConstantsEnum() {
    doTest(true);
  }

  public void testFieldnameconstants$FieldNameConstantsHandrolled() {
    doTest(true);
  }

}
