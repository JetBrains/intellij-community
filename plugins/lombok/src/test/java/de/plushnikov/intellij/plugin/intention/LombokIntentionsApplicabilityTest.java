package de.plushnikov.intellij.plugin.intention;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class LombokIntentionsApplicabilityTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return LombokTestUtil.getTestDataPath();
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  @Override
  protected String getBasePath() {
    return "/intentions";
  }

}
