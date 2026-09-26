package de.plushnikov.intellij.plugin.highlights;

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

  public void testSneakThrowsDoesntCatchExceptionFromSuperConstructor() {
    doTest();
  }

  public void testSneakThrowsDoesntCatchExceptionFromThisConstructor() {
    doTest();
  }

  public void testSneakyThrowsTryInsideLambda() {
    doTest();
  }

  public void testSneakyThrowsTryWithResources() {
    doTest();
  }
}
