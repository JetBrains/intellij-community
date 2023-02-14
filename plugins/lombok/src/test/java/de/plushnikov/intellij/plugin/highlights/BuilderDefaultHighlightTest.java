package de.plushnikov.intellij.plugin.highlights;

/**
 * @author Lekanich
 */
public class BuilderDefaultHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/builderDefault";
  }

  public void testBuilderDefaultHighlighting() {
    doTest();
  }
}
