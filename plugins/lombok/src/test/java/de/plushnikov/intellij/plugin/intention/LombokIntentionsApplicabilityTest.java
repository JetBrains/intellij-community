package de.plushnikov.intellij.plugin.intention;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class LombokIntentionsApplicabilityTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/');
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
    return "/plugins/lombok/testData/intentions";
  }

}
