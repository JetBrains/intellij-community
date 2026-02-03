package de.plushnikov.intellij.plugin.configsystem;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class DataTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/data";
  }

  public void testExtraPrivate$DataTest() throws IOException {
    doTest();
  }

  public void testExtraPrivate$DataNegativeTest() throws IOException {
    doTest();
  }
}
