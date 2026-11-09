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
public class RedundantSlf4jDefinitionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new RedundantSlf4jDefinitionInspection(), "/inspection/redundantSlf4jDeclaration");

  @Test
  public void testRedundantSlf4jDefinition() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix("Replace logger field with @Slf4j annotation");
  }

  @Test
  public void testRedundantSlf4jDefinitionInPackage() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix("Replace logger field with @Slf4j annotation");
  }
}
