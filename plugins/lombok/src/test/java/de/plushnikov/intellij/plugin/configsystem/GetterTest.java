package de.plushnikov.intellij.plugin.configsystem;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class GetterTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/getter";
  }

  public void testNoIsPrefix$GetterClassTest() throws IOException {
    doTest();
  }

  public void testNoIsPrefix$GetterFieldTest() throws IOException {
    doTest();
  }

}