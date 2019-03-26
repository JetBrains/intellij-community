package com.intellij.bash.template;

import com.intellij.bash.BashFileType;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class BashBaseFixtureTestCase extends LightPlatformCodeInsightFixtureTestCase {

  protected abstract String getDataPath();

  @Override
  protected String getTestDataPath() {
    return new File(getDataPath()).getAbsolutePath();
  }

  protected void configureByFile() {
    myFixture.configureByFile(getTestName());
  }

  protected String getAfterTestName() {
    return getTestName(true) + "." + getAfterExtension();
  }

  @NotNull
  private String getTestName() {
    return getTestName(true) + "." + getExtension();
  }

  @NotNull
  private String getExtension() {
    return BashFileType.INSTANCE.getDefaultExtension();
  }

  @NotNull
  private String getAfterExtension() {
    return "after." + BashFileType.INSTANCE.getDefaultExtension();
  }
}
