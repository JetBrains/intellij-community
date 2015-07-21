package com.jetbrains.python.edu;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;

/**
 * @author traff
 */
public class PyRunCurrentFileAction extends PyRunConfigurationForFileAction {

  public PyRunCurrentFileAction() {
    getTemplatePresentation().setIcon(AllIcons.Actions.Execute);
  }

  @Override
  protected String getConfigurationType() {
    return "Run";
  }

  @Override
  protected void runConfiguration(RunnerAndConfigurationSettings configuration) {
    ExecutionUtil.runConfiguration(configuration, DefaultRunExecutor.getRunExecutorInstance());
  }
}
