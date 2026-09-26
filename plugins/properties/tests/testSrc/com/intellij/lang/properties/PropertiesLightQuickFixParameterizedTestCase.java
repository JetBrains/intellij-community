package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.testFramework.common.BazelTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.common.BazelTestUtil.getFileFromBazelRuntime;

abstract class PropertiesLightQuickFixParameterizedTestCase extends LightQuickFixParameterizedTestCase {
  @NonNls
  private static final String JAVA_TEST_DATA_PATH = "java/java-tests/testData";

  @Override
  protected @NotNull @NonNls String getTestDataPath() {
    if (BazelTestUtil.isUnderBazelTest()) {
      var label = BazelLabel.Companion.fromString("@community//java/java-tests:testData");
      return getFileFromBazelRuntime(label).toAbsolutePath().toString();
    }
    return PathManagerEx.getCommunityHomePath() + "/" + JAVA_TEST_DATA_PATH;
  }
}
