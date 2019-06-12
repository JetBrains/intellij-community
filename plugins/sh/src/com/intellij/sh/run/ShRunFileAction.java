// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.sh.parser.ShShebangParserUtil;
import com.intellij.sh.psi.ShFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class ShRunFileAction extends DumbAwareAction {
  static final String ID = "runShellFileAction";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (!(file instanceof ShFile)) return;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;

    Project project = file.getProject();
    RunnerAndConfigurationSettings configurationSettings =
      RunManager.getInstance(project).createConfiguration(file.getName(), ShConfigurationType.class);
    ShRunConfiguration runConfiguration = (ShRunConfiguration)configurationSettings.getConfiguration();
    runConfiguration.setScriptPath(virtualFile.getPath());
    String defaultShell = ObjectUtils.notNull(EnvironmentUtil.getValue("SHELL"), "/bin/sh");
    runConfiguration.setInterpreterPath(ObjectUtils.notNull(ShShebangParserUtil.getShebangExecutable((ShFile)file), defaultShell));

    ExecutionEnvironmentBuilder builder =
      ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), runConfiguration);
    if (builder != null) {
      ExecutionManager.getInstance(project).restartRunProfile(builder.build());
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabled(e));
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    return e.getProject() != null && e.getData(CommonDataKeys.PSI_FILE) instanceof ShFile;
  }
}