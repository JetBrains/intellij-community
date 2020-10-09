package de.plushnikov.intellij.plugin.highlights;

/**
 * @author Lekanich
 */
public class SneakyThrowsHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/sneakyThrows";
  }

  public void testSneakThrowsDoesntCatchCaughtException() {
    doTest();
  }

  public void testSneakThrowsDoesntCatchCaughtExceptionNested() {
    doTest();
  }
}
