package de.plushnikov.intellij.plugin.configsystem;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class JacksonizedBuilderTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/builder";
  }

  @Override
  protected boolean shouldCompareAnnotations() {
    return true;
  }

  public void testJacksonized$JacksonizedBuilderComplex() {
    doTest();
  }
}
