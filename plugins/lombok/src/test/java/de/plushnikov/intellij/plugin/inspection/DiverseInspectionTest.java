package de.plushnikov.intellij.plugin.inspection;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

/**
 * @author Plushnikov Michail
 */
@TestApplication
public class DiverseInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new LombokInspection(), "/inspection/diverse");

  @Test
  public void testDataEqualsAndHashCodeOverride() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testEqualsAndHashCodeCallSuper() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testIssue37() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testSetterOnFinalField() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testNoArgsConstructorWithRequiredFieldsShouldBeForced() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testNoArgsConstructorWithNotNullFieldsShouldBeNotForced() {
    inspectionFixture.get().doTest();
  }

}
