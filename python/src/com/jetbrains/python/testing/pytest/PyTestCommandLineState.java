package com.jetbrains.python.testing.pytest;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
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
    cmd.addParameters("-p", "pytest_teamcity");
    cmd.addParameter(myConfiguration.getTestToRun());
    String keywords = myConfiguration.getKeywords();
    if (!StringUtil.isEmptyOrSpaces(keywords)) {
      cmd.addParameters("-k", "\"" + keywords + "\"");
    }
  }
}
