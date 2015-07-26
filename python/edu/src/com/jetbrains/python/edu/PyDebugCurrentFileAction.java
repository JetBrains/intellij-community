package com.jetbrains.python.edu;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.jetbrains.python.edu.debugger.PyEduDebugExecutor;

public class PyDebugCurrentFileAction extends PyRunConfigurationForFileAction {

  public PyDebugCurrentFileAction() {
    getTemplatePresentation().setIcon(AllIcons.Actions.StartDebugger);
  }

  @Override
  protected String getConfigurationType() {
    return "Debug";
  }

  @Override
  protected void runConfiguration(RunnerAndConfigurationSettings configuration) {
    ExecutionUtil.runConfiguration(configuration, PyEduDebugExecutor.getInstance());
  }
}
