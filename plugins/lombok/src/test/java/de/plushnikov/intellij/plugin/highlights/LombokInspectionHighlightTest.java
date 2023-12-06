package de.plushnikov.intellij.plugin.highlights;

import com.intellij.codeInspection.InspectionProfileEntry;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;

public class LombokInspectionHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/builderDefault";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testBuilderDefaultWithoutBuilderAnnotation() {
    doTest();
  }
}
