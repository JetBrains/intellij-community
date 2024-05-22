package com.intellij.java.lomboktest;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.project.IncompleteDependenciesServiceKt.asAutoCloseable;

public class LombokIncompleteModeHighlightingTest extends LightDaemonAnalyzerTestCase {

  public void testLombokBasics() { doTest(); }

  public void testLombokBasicsWithExplicitImport() { doTest(); }

  public void testLombokLogs() { doTest(); }

  private void doTest() {
    doTest(getTestName(false) + ".java");
  }

  private void doTest(String fileName) {
    IncompleteDependenciesService service = getProject().getService(IncompleteDependenciesService.class);
    try (var ignored = asAutoCloseable(WriteAction.compute(() -> service.enterIncompleteState()))) {
      doTest("/plugins/lombok/testData/highlightingIncompleteMode/" + fileName, true, true);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected @NonNls @NotNull String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath();
  }
}
