// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal

public class PyUpdateProjectSdkAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(PyUpdateProjectSdkAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        Sdk sdk = PythonSdkUtil.findPythonSdk(module);
        if (sdk == null) {
          LOG.info("Skipping module " + module + " as not having a Python SDK");
          continue;
        }
        PythonSdkUpdater.scheduleUpdate(sdk, project);
      }
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
