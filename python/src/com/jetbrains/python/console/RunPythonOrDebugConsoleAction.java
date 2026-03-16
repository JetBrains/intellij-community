// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.actions.PyExecuteInConsole;
import com.jetbrains.python.icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

public class RunPythonOrDebugConsoleAction extends AnAction implements DumbAware {

  public RunPythonOrDebugConsoleAction() {
    super();
    getTemplatePresentation().setIcon(PythonIcons.Python.PythonConsole);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(false);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      Pair<Sdk, Module> sdkAndModule = PydevConsoleRunnerUtil.findPythonSdkAndModule(project, e.getData(PlatformCoreDataKeys.MODULE));
      if (sdkAndModule.first != null) {
        e.getPresentation().setEnabled(true);
      }
    }
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    PyExecuteInConsole.executeCodeInConsole(project, (String)null, null, true, true, true, null);
  }
}