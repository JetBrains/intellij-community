package de.plushnikov.intellij.plugin.highlights;

public class WithHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/with";
  }

  public void testWithOnRecord() {
    doTest();
  }

  public void testWitherExample() {
    doTest();
  }

}
