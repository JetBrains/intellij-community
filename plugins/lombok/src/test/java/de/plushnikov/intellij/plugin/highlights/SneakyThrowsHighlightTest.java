package de.plushnikov.intellij.plugin.highlights;

/**
 * @author Lekanich
 */
public class SneakyThrowsHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/sneakyThrows";
  }

  public void testSneakThrowsDoesntCatchCaughtException() {
    doTest();
  }

  public void testSneakThrowsDoesntCatchCaughtExceptionNested() {
    doTest();
  }
}
