package com.intellij.debugger.streams;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class StreamChainBuilderFixtureTestCase extends LightCodeInsightTestCase {

  StreamChainBuilderFixtureTestCase() {
    super();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return new File("testData/" + getRelativeTestPath()).getAbsolutePath();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JdkManager.getMockJdk18();
  }

  @NotNull
  @Override
  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @NotNull
  protected abstract String getRelativeTestPath();
}
