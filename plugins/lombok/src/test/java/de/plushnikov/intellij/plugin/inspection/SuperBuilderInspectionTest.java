package de.plushnikov.intellij.plugin.inspection;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

@TestApplication
public class SuperBuilderInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new LombokInspection(), "/inspection/superbuilder");

  @Test
  public void testBuilderDefaultValue() {
    final BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
    if (193 <= buildNumber.getBaselineVersion()) {
      inspectionFixture.get().doNamedTest(inspectionFixture.get().getTestName(false) + "193");
    } else {
      inspectionFixture.get().doTest();
    }
  }

  @Test
  public void testBuilderInvalidIdentifier() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testBuilderRightType() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testBuilderDefaultsWarnings() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testBuilderInvalidUse() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testBuilderInvalidInnerBuilderClass() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testBuilderObtainVia() {
    inspectionFixture.get().doTest();
  }
}
