package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.RecursionManager;

public class BuilderInspectionTest extends LombokInspectionTest {

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/builder";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testBuilderDefaultValue() {
    final BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
    if (193 <= buildNumber.getBaselineVersion()) {
      doNamedTest(getTestName(false) + "193");
    } else {
      doTest();
    }
  }

  public void testBuilderInvalidIdentifier() {
    doTest();
  }

  public void testBuilderRightType() {
    doTest();
  }

  public void testBuilderDefaultsWarnings() {
    doTest();
  }

  public void testBuilderInvalidUse() {
    doTest();
  }

  public void testBuilderObtainVia() {
    doTest();
  }
}
