package de.plushnikov.intellij.plugin.inspection;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

@TestApplication
public class CleanupInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new LombokInspection(), "/inspection/cleanup");

  @Test
  public void testCleanupExample() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testCleanupEmptyValue() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testCleanupWithoutInitializer() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testCleanupWrongElement() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testCleanupWrongValue() {
    inspectionFixture.get().doTest();
  }

}
