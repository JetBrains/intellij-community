package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ToolbarPanel;
import com.intellij.openapi.actionSystem.DefaultActionGroup;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerToolbarPanel extends ToolbarPanel {
  public SMTRunnerToolbarPanel(final TestConsoleProperties properties,
                               final RunnerSettings runnerSettings,
                               final ConfigurationPerRunnerSettings configurationSettings,
                               final TestFrameworkRunningModel model, JComponent contentPane) {
    super(properties, runnerSettings, configurationSettings, contentPane);
    //TODO rerun failed test
    //TODO coverage
    setModel(model);
  }

  protected void appendAdditionalActions(final DefaultActionGroup actionGroup,
                                         final TestConsoleProperties properties,
                                         final RunnerSettings runnerSettings,
                                         final ConfigurationPerRunnerSettings configurationSettings, JComponent parent) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void setModel(final TestFrameworkRunningModel model) {
    //TODO: RunningTestTracker - for tracking current test
    super.setModel(model);
  }
}
