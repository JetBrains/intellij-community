package com.intellij.execution.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.Messages;

public class RunContextAction extends BaseRunConfigurationAction {
  private final JavaProgramRunner myRunner;

  public RunContextAction(final JavaProgramRunner runner) {
    super(runner.getInfo().getStartActionText() + " context configuration", null, runner.getInfo().getIcon());
    myRunner = runner;
  }

  protected void perform(final ConfigurationContext context) {
    RunnerAndConfigurationSettingsImpl configuration = context.findExisting();
    final RunManagerEx runManager = context.getRunManager();
    if (configuration == null) {
      configuration = context.getConfiguration();
      if (configuration == null) return;
      runManager.setTemporaryConfiguration(configuration);
    }
    runManager.setActiveConfiguration(configuration);
    try {
      RunStrategy.getInstance().execute(configuration.getConfiguration(), context.getDataContext(), myRunner,
                                        configuration.getRunnerSettings(myRunner), configuration.getConfigurationSettings(myRunner));
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(context.getProject(), e.getMessage(), "Error");
    }
  }

  protected void updatePresentation(final Presentation presentation, final String actionText, final ConfigurationContext context) {
    presentation.setText(myRunner.getInfo().getStartActionText() + actionText, true);
  }
}
