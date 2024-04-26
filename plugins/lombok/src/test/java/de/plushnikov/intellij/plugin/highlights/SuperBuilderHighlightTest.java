package de.plushnikov.intellij.plugin.highlights;

import com.intellij.codeInspection.InspectionProfileEntry;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;

public class SuperBuilderHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/superBuilder";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testSuperBuilderOnInnerClass() {
    doTest();
  }
}
