// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.icons.PythonPsiApiIcons;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.run.PythonRunConfigurationProducer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class PyRunFileInConsoleAction extends AnAction implements DumbAware {
  private static final HashMap<PythonRunConfiguration, Boolean> waitingForExecution = new HashMap<>();

  public PyRunFileInConsoleAction() {
    super(PyBundle.messagePointer("acton.run.file.in.python.console.title"),
          PyBundle.messagePointer("action.run.file.in.python.console.description"), PythonPsiApiIcons.Python);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(project != null && psiFile instanceof PyFile);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (file == null) return;
    final Project project = e.getProject();
    if (project == null) return;
    final ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext(), e.getPlace());
    PythonRunConfigurationProducer configProducer = RunConfigurationProducer.getInstance(PythonRunConfigurationProducer.class);
    RunnerAndConfigurationSettings settings = configProducer.findExistingConfiguration(context);
    RunManager runManager = RunManager.getInstance(project);
    if (settings == null) {
      final ConfigurationFromContext fromContext = configProducer.createConfigurationFromContext(context);
      if (fromContext == null) return;
      settings = fromContext.getConfigurationSettings();
      runManager.setTemporaryConfiguration(settings);
    }
    final PythonRunConfiguration configuration = (PythonRunConfiguration)settings.getConfiguration();
    runManager.setSelectedConfiguration(settings);
    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), settings);
    if (builder != null) {
      boolean oldValueShowCommandline = configuration.showCommandLineAfterwards();
      configuration.setShowCommandLineAfterwards(true);
      synchronized (this) {
        waitingForExecution.put(configuration, oldValueShowCommandline);
      }

      ExecutionManager.getInstance(project).restartRunProfile(builder.build());
    }
  }

  /*
    Restore the option which was changed for the action execution
   */
  synchronized public static void configExecuted(PythonRunConfiguration configuration) {
    if (waitingForExecution.containsKey(configuration)) {
      Boolean oldValue = waitingForExecution.remove(configuration);
      configuration.setShowCommandLineAfterwards(oldValue);
    }
  }
}
