package de.plushnikov.intellij.plugin.highlights;

public class LombokInspectionHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/builderDefault";
  }

  public void testBuilderDefaultWithoutBuilderAnnotation() {
    doTest();
  }
}
