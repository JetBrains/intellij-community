package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.ToolbarPanel;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.openapi.actionSystem.DefaultActionGroup;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitToolbarPanel extends ToolbarPanel {
  public RTestUnitToolbarPanel(final TestConsoleProperties properties,
                               final RunnerSettings runnerSettings,
                               final ConfigurationPerRunnerSettings configurationSettings,
                               final TestFrameworkRunningModel model) {
    super(properties, runnerSettings, configurationSettings);
    //TODO rerun failed test
    //TODO coverage
    setModel(model);
  }

  protected void appendAdditionalActions(final DefaultActionGroup actionGroup,
                                         final TestConsoleProperties properties,
                                         final RunnerSettings runnerSettings,
                                         final ConfigurationPerRunnerSettings configurationSettings) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void setModel(final TestFrameworkRunningModel model) {
    //TODO: RunningTestTracker - for tracking current test
    super.setModel(model);
  }
}
