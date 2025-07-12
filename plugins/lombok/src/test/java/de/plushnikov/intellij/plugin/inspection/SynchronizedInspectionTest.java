package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class SynchronizedInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/synchronized";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testSynchronizedTest() {
    doTest();
  }

  public void testSynchronizedTypeErrors() {
    doTest();
  }

}
