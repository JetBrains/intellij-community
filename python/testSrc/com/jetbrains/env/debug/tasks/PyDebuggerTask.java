// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.run.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class PyDebuggerTask extends PyCustomConfigDebuggerTask {
  public PyDebuggerTask(@Nullable final String relativeTestDataPath, String scriptName, String scriptParameters) {
    super(relativeTestDataPath);
    setScriptName(scriptName);
    setScriptParameters(scriptParameters);
    init();
  }

  public PyDebuggerTask(@Nullable final String relativeTestDataPath, String scriptName) {
    this(relativeTestDataPath, scriptName, null);
  }

  protected void init() {

  }

  @Nullable
  @Override
  public Set<String> getTagsToCover() {
    return Sets.newHashSet("python2.7", "python3.5", "python3.6", "python3.7", "python3.8");
  }

  protected boolean usePytestRunner() {
    return false;
  }

  @Override
  protected AbstractPythonRunConfiguration createRunConfiguration(@NotNull String sdkHome, @Nullable Sdk existingSdk) {
    ConfigurationFactory factory = PythonConfigurationType.getInstance().getConfigurationFactories()[0];
    mySettings = RunManager.getInstance(getProject()).createConfiguration("test", factory);
    PythonRunConfiguration runConfiguration = (PythonRunConfiguration)mySettings.getConfiguration();
    runConfiguration.setSdkHome(sdkHome);
    runConfiguration.setSdk(existingSdk);
    runConfiguration.setScriptName(getScriptName());
    runConfiguration.setWorkingDirectory(myFixture.getTempDirPath());
    runConfiguration.setScriptParameters(getScriptParameters());
    runConfiguration.setEnvs(getEnvs());
    return runConfiguration;
  }

  protected @NotNull Map<String, String> getEnvs() {
    return ImmutableMap.of();
  }

  public @Nullable PythonRunConfiguration getRunConfiguration() {
    return myRunConfiguration != null ? (PythonRunConfiguration)myRunConfiguration : null;
  }

  @Override
  @NotNull
  protected String output() {
    String consoleNotAvailableMessage = "Console output not available.";
    if (mySession != null && mySession.getConsoleView() != null) {
      PythonDebugLanguageConsoleView pydevConsoleView = (PythonDebugLanguageConsoleView)mySession.getConsoleView();
      ConsoleViewImpl consoleView = pydevConsoleView.getTextConsole();
      return consoleView != null ? XDebuggerTestUtil.getConsoleText(consoleView) : consoleNotAvailableMessage;
    }
    return consoleNotAvailableMessage;
  }
}
