package de.plushnikov.intellij.plugin.highlights;

import com.intellij.codeInspection.InspectionProfileEntry;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;

public class RecordsHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/onRecord";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testInvalidLombokAnnotationsOnRecord() {
    doTest();
  }
}
