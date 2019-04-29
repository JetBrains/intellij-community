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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.run.PythonRunConfigurationParams;
import com.jetbrains.python.run.PythonRunConfigurationProducer;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

public class PyRunFileInConsoleAction extends AnAction implements DumbAware {
  public PyRunFileInConsoleAction() {
    super("Run File in Python Console", "Run Current File in Python Console", PythonIcons.Python.Python);
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
    final ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext());
    final ConfigurationFromContext fromContext =
      RunConfigurationProducer.getInstance(PythonRunConfigurationProducer.class).createConfigurationFromContext(context);
    if (fromContext == null) return;
    final RunnerAndConfigurationSettings settings = fromContext.getConfigurationSettings();
    final PythonRunConfigurationParams configuration = (PythonRunConfigurationParams)settings.getConfiguration();
    configuration.setShowCommandLineAfterwards(true);
    RunManager runManager = RunManager.getInstance(project);
    runManager.setTemporaryConfiguration(settings);
    runManager.setSelectedConfiguration(settings);
    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), settings);
    if (builder != null) {
      ExecutionManager.getInstance(project).restartRunProfile(builder.build());
    }
  }
}
