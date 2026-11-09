package de.plushnikov.intellij.plugin.highlights;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

/**
 * Test for highlighting issues with @Data and @Value annotations in inheritance scenarios
 */
@TestApplication
public class DataValueInheritanceHighlightTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new LombokInspection(), "/highlights/dataValueInheritance");

  @Test
  public void testDataInheritanceHighlighting() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testValueInheritanceHighlighting() {
    inspectionFixture.get().doTest();
  }
}