package de.plushnikov.intellij.plugin.inspection;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import com.siyeh.ig.style.FieldMayBeFinalInspection;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

@TestApplication
public class LombokFieldMayBeFinalInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new FieldMayBeFinalInspection(), "/inspection/canBeFinalInspection");

  @Test
  public void testClassNormal() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testClassWithData() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testClassWithFieldSetter() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testClassWithGetter() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testClassWithSetter() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testClassWithValue() {
    inspectionFixture.get().doTest();
  }

}
