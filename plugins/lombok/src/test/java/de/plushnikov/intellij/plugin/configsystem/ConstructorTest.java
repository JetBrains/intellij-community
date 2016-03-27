package de.plushnikov.intellij.plugin.configsystem;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class ConstructorTest extends AbstractLombokConfigSystemTestCase {

  protected boolean shouldCompareAnnotations() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/constructor";
  }

  public void testSuppressConstructorProperties$NoArgsConstructorTest() throws IOException {
    doTest();
  }

  public void testSuppressConstructorProperties$AllArgsConstructorTest() throws IOException {
    doTest();
  }

  public void testSuppressConstructorProperties$RequiredArgsConstructorTest() throws IOException {
    doTest();
  }
}