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
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.jetbrains.extensions.python.VirtualFileExtKt;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.run.PythonRunConfigurationParams;
import com.jetbrains.python.run.PythonRunConfigurationProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyRunFileInConsoleAction extends AnAction implements DumbAware {
  public PyRunFileInConsoleAction() {
    super("Run File in Console");
    getTemplatePresentation().setIcon(AllIcons.Actions.Execute);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    final Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    PsiFile psiFile = getFile(e, project);

    presentation.setEnabled(psiFile instanceof PyFile);
  }

  @Nullable
  private static PsiFile getFile(@NotNull final AnActionEvent e, @NotNull final Project project) {
    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext());

    if (psiFile == null) {
      final VirtualFile currentFile = FileEditorManagerEx.getInstanceEx(project).getCurrentFile();
      if (currentFile != null) {
        psiFile = ObjectUtils.tryCast(VirtualFileExtKt.toPsi(currentFile, project), PsiFile.class);
      }
    }
    return psiFile;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    final PsiFile file = getFile(e, project);
    if (file == null) return;


    final ConfigurationContext context = ConfigurationContext.createEmptyContextForLocation(PsiLocation.fromPsiElement(file));

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
