package de.plushnikov.intellij.plugin.highlights;

import com.intellij.codeInspection.InspectionProfileEntry;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;

public class SneakyThrowsHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/sneakyThrows";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
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
}
