package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.openapi.util.RecursionManager;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class FieldDefaultsTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/fieldDefaults";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  //region DefaultFinal
  public void testDefaultFinal$DefaultFinalFieldTest() throws IOException {
    doTest();
  }

  public void testDefaultFinal$DefaultFinalFieldWithFieldDefaultsTest() throws IOException {
    doTest();
  }

  public void testDefaultFinal$DefaultFinalFieldWithNonFinalTest() throws IOException {
    doTest();
  }

  public void testDefaultFinal$DefaultFinalFieldWithUnrelatedFieldDefaultsTest() throws IOException {
    doTest();
  }
  //endregion

  //region DefaultPrivate
  public void testDefaultPrivate$DefaultPrivateFieldTest() throws IOException {
    doTest();
  }

  public void testDefaultPrivate$DefaultPrivateFieldWithFieldDefaultsTest() throws IOException {
    doTest();
  }

  public void testDefaultPrivate$DefaultPrivateFieldWithPackagePrivateTest() throws IOException {
    doTest();
  }

  public void testDefaultPrivate$DefaultPrivateFieldWithUnrelatedFieldDefaultsTest() throws IOException {
    doTest();
  }
  //endregion
}
