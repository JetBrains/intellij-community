// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.ut;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.List;

import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;

@EnvTestTagsRequired(tags = "anaconda")
public class PyAnacondaCompletionPerformanceTest extends PyEnvTestCase {
  @Test
  public void completingSysVersion() {
    doCompletionResultTest("sys.version");
  }

  @Test
  public void performance() {
    runPythonTest(new CompletionDurationTask(getTestName(false), 200));
  }

  private void doCompletionResultTest(@NotNull String expectedVariant) {
    runPythonTest(new CompletionTask(getTestName(false), expectedVariant));
  }

  private static class CompletionTask extends PyExecutionFixtureTestTask {
    private final String myExpectedSuggestion;

    CompletionTask(@NotNull String testDataPath, @NotNull String expectedSuggestion) {
      super(testDataPath);
      myExpectedSuggestion = expectedSuggestion;
    }

    @Override
    public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
      createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_AND_SKELETONS);
      myFixture.configureByFile("main.py");
      myFixture.completeBasic();
      List<String> variants = myFixture.getLookupElementStrings();
      assertContainsElements(variants, myExpectedSuggestion);
    }

    @Override
    protected @NotNull String getTestDataPath() {
      return super.getTestDataPath() + "/completion/env/anaconda";
    }
  }

  private static class CompletionDurationTask extends PyExecutionFixtureTestTask {
    private final int myExpectedDuration;

    CompletionDurationTask(@NotNull String testDataPath, int expectedDuration) {
      super(testDataPath);
      myExpectedDuration = expectedDuration;
    }

    @Override
    public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
      createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_AND_SKELETONS);
      myFixture.configureByFile("main.py");
      EdtTestUtil.runInEdtAndWait(() -> {
        PlatformTestUtil.startPerformanceTest("Basic completion", myExpectedDuration, this::doCompleteBasic)
          .assertTiming();
      });
    }

    private void doCompleteBasic() {
      myFixture.completeBasic();
      LookupManager.hideActiveLookup(getProject());
    }

    @Override
    protected @NotNull String getTestDataPath() {
      return super.getTestDataPath() + "/completion/env/anaconda";
    }
  }
}
