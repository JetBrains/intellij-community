package de.plushnikov.intellij.plugin.highlights;

public class SuperBuilderHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/superBuilder";
  }

  public void testSuperBuilderOnInnerClass() {
    doTest();
  }
}
