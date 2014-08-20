package com.jetbrains.python.edu;

import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyRunCurrentFileAction extends AnAction {
  public PyRunCurrentFileAction() {
    getTemplatePresentation().setIcon(AllIcons.Actions.Execute);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext());
    Location location = context.getLocation();
    if (location != null && location.getPsiElement().getContainingFile() != null && location.getPsiElement().getContainingFile().getFileType() == PythonFileType.INSTANCE) {
      presentation.setEnabled(true);
      presentation.setText("Run '" + location.getPsiElement().getContainingFile().getName() + "'");
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext());

    run(context);
  }

  public static void run(@NotNull ConfigurationContext context) {
    RunnerAndConfigurationSettings configuration = context.findExisting();
    final RunManagerEx runManager = (RunManagerEx)context.getRunManager();
    if (configuration == null) {
      configuration = context.getConfiguration();
      if (configuration == null) {
        return;
      }
      runManager.setTemporaryConfiguration(configuration);
    }
    runManager.setSelectedConfiguration(configuration);

    ExecutionUtil.runConfiguration(configuration, DefaultRunExecutor.getRunExecutorInstance());
  }
}
