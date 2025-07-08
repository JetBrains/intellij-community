package de.plushnikov.intellij.plugin.highlights;

/**
 * Test for highlighting issues with @Data and @Value annotations in inheritance scenarios
 */
public class DataValueInheritanceHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/dataValueInheritance";
  }

  public void testDataInheritanceHighlighting() {
    doTest();
  }

  public void testValueInheritanceHighlighting() {
    doTest();
  }
}