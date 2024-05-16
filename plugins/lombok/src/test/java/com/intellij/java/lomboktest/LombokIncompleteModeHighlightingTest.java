package com.intellij.java.lomboktest;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LombokIncompleteModeHighlightingTest extends LightDaemonAnalyzerTestCase {

  public void testLombokBasics() { doTest(); }

  public void testLombokBasicsWithExplicitImport() { doTest(); }

  private void doTest() {
    doTest(getTestName(false) + ".java");
  }

  private void doTest(String fileName) {
    var ignored = WriteAction.compute(() -> getProject().getService(IncompleteDependenciesService.class).enterIncompleteState());
    try {
      doTest("/plugins/lombok/testData/highlightingIncompleteMode/" + fileName, true, true);
    }
    finally {
      WriteAction.run(ignored::close);
    }
  }

  @Override
  protected @NonNls @NotNull String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath();
  }
}
