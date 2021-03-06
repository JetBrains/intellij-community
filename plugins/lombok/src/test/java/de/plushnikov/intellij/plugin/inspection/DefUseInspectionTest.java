package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.defUse.DefUseInspection;
import org.jetbrains.annotations.Nullable;


public class DefUseInspectionTest extends LombokInspectionTest {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/diverse";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new DefUseInspection();
  }

  public void testIssue633() {
    doTest();
  }
}
