package com.jetbrains.python.testing.pytest;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;

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
    cmd.setExePath(myConfiguration.getRunnerScriptPath());
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
