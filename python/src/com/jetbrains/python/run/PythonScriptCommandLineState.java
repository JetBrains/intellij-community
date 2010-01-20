package com.jetbrains.python.run;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.text.StringUtil;

import java.util.List;

/**
 * @author yole
 */
public class PythonScriptCommandLineState extends PythonCommandLineState {
  private final PythonRunConfiguration myConfig;

  public PythonScriptCommandLineState(PythonRunConfiguration runConfiguration, ExecutionEnvironment env, List<Filter> filters) {
    super(runConfiguration, env, filters);
    myConfig = runConfiguration;
  }

  @Override
  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
    commandLine.getParametersList().addParametersString(myConfig.getInterpreterOptions());

    if (!StringUtil.isEmptyOrSpaces(myConfig.getScriptName())) {
      commandLine.addParameter(myConfig.getScriptName());
    }

    commandLine.getParametersList().addParametersString(myConfig.getScriptParameters());

    if (!StringUtil.isEmptyOrSpaces(myConfig.getWorkingDirectory())) {
      commandLine.setWorkDirectory(myConfig.getWorkingDirectory());
    }
  }
}
