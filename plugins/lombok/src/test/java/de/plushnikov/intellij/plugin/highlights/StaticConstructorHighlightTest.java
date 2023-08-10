package de.plushnikov.intellij.plugin.highlights;

public class StaticConstructorHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/staticConstructor";
  }

  public void testDataDto() {
    doTest();
  }

  public void testValueDto() {
    doTest();
  }
}
