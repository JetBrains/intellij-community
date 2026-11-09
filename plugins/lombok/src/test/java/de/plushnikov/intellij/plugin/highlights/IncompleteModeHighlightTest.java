package de.plushnikov.intellij.plugin.highlights;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.project.IncompleteDependenciesServiceKt.asAutoCloseable;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

@TestApplication
public class IncompleteModeHighlightTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new LombokInspection(), "/highlights/incomplete");

  @Test
  public void testLogs() {
    doIncompleteTest();
  }

  private void doIncompleteTest() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IncompleteDependenciesService service = myFixture.getProject().getService(IncompleteDependenciesService.class);
    try (var ignored = asAutoCloseable(WriteAction.compute(() -> service.enterIncompleteState(this)))) {
      String name = inspectionFixture.get().getTestName(false);
      myFixture.configureByFile(name + ".java");
      myFixture.testHighlighting(true, true, true);
    }
  }
}
