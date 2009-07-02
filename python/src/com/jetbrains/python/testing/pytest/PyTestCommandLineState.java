package com.jetbrains.python.testing.pytest;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;

import java.io.File;

/**
 * @author yole
 */
public class PyTestCommandLineState extends PythonTestCommandLineStateBase {
  private PyTestRunConfiguration myConfiguration;

  public PyTestCommandLineState(PyTestRunConfiguration configuration, ExecutionEnvironment env) {
    super(configuration, env);
    myConfiguration = configuration;
  }

  @Override
  protected void setRunnerPath(GeneralCommandLine cmd) {
    String sdkHome = myConfiguration.getSdkHome();
    String runnerExt = SystemInfo.isWindows ? ".exe" : "";
    File runner = new File(sdkHome, "scripts/py.test" + runnerExt);
    cmd.setExePath(runner.getPath());
  }

  protected void addTestRunnerParameters(GeneralCommandLine cmd) {
    cmd.addParameter("-p");
    cmd.addParameter("pytest_teamcity");
    cmd.addParameter(myConfiguration.getTestToRun());
  }
}
