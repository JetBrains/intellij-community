/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.jetbrains.annotations.NotNull;

public class PyRunFileInConsoleAction extends AnAction implements DumbAware {
  public PyRunFileInConsoleAction() {
    super("Run File in Console");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final Project project = e.getProject();
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(project != null && psiFile instanceof PyFile);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
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
