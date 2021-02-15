// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.actions.PyExecuteInConsole;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

public class RunPythonOrDebugConsoleAction extends AnAction implements DumbAware {

  public RunPythonOrDebugConsoleAction() {
    super();
    getTemplatePresentation().setIcon(PythonIcons.Python.PythonConsole);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(false);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      Pair<Sdk, Module> sdkAndModule = PydevConsoleRunner.findPythonSdkAndModule(project, e.getData(LangDataKeys.MODULE));
      if (sdkAndModule.first != null) {
        e.getPresentation().setEnabled(true);
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    PyExecuteInConsole.executeCodeInConsole(project, null, null, true, true, true, null);
  }
}
