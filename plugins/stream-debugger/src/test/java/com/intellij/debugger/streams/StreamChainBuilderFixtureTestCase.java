package com.intellij.debugger.streams;

import com.intellij.testFramework.PsiTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class StreamChainBuilderFixtureTestCase extends PsiTestCase {
  public StreamChainBuilderFixtureTestCase() {
    super();
  }

  @Override
  protected String getTestDataPath() {
    return new File("testData/" + getRelativeTestPath()).getAbsolutePath();
  }

  @NotNull
  protected abstract String getRelativeTestPath();
}
