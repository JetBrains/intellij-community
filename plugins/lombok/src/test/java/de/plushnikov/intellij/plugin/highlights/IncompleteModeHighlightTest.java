package de.plushnikov.intellij.plugin.highlights;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.project.IncompleteDependenciesServiceKt.asAutoCloseable;

public class IncompleteModeHighlightTest extends AbstractLombokHighlightsTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/incomplete";
  }

  public void testLogs() {
    doIncompleteTest();
  }

  private void doIncompleteTest() {
    IncompleteDependenciesService service = getProject().getService(IncompleteDependenciesService.class);
    try (var ignored = asAutoCloseable(WriteAction.compute(() -> service.enterIncompleteState(this)))) {
      String name = getTestName(false);
      myFixture.configureByFile(name + ".java");
      myFixture.testHighlighting(true, true, true);
    }
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST;
  }
}
