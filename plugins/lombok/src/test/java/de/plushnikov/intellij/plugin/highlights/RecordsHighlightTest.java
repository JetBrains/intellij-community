package de.plushnikov.intellij.plugin.highlights;

public class RecordsHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/onRecord";
  }

  public void testInvalidLombokAnnotationsOnRecord() {
    doTest();
  }
}
