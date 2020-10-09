package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.openapi.util.RecursionManager;

public class FieldNameConstantsTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/fieldnameconstants";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testConfiguration$FieldNameConstantsConfigKeys() {
    doTest();
  }

  public void testUppercase$FieldNameConstantsUppercased() {
    doTest();
  }
}
