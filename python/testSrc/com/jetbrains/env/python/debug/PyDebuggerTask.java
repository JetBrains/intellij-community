// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.debug;

import com.google.common.collect.Sets;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.run.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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
    return Sets.newHashSet("python2.7", "python3.5", "python3.6", "python3.7", "python3.8", "jython", "IronPython", "pypy");
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
    runConfiguration.setScriptName(getScriptName());
    runConfiguration.setWorkingDirectory(myFixture.getTempDirPath());
    runConfiguration.setScriptParameters(getScriptParameters());
    return runConfiguration;
  }

  @Override
  protected CommandLinePatcher[] createCommandLinePatchers(PyDebugRunner runner, PythonCommandLineState pyState, RunProfile profile,
                                                           int serverLocalPort) {
    final CommandLinePatcher[] debugPatchers = runner.createCommandLinePatchers(myFixture.getProject(), pyState, profile, serverLocalPort);
    if (!usePytestRunner()) {
      return debugPatchers;
    }
    ArrayList<CommandLinePatcher> result = new ArrayList<>();
    result.add(pytestPatcher());
    result.addAll(Arrays.asList(debugPatchers));
    return result.toArray(new CommandLinePatcher[0]);
  }

  private static CommandLinePatcher pytestPatcher() {
    return new CommandLinePatcher() {
      @Override
      public void patchCommandLine(GeneralCommandLine commandLine) {
        final ParamsGroup scriptGroup = commandLine.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
        scriptGroup.addParameterAt(0, "--path");
        scriptGroup.addParameterAt(0, PythonHelper.PYTEST.asParamString());

        commandLine.getEnvironment().put("PYTEST_RUN_CONFIG", "True");
      }
    };
  }

  public @Nullable PythonRunConfiguration getRunConfiguration() {
    return myRunConfiguration != null ? (PythonRunConfiguration)myRunConfiguration : null;
  }
}
