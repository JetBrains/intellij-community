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
public class DelegateInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new LombokInspection(), "/inspection/delegate");

  @Test
  public void testConcreteType() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testOnMethodWithParameter() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testOnStaticFieldOrMethod() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testRecursionType() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testDelegateIncludeUnknownMethods() {
    inspectionFixture.get().doTest();
  }

}
