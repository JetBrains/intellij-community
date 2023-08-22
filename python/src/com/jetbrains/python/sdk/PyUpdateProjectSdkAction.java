// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

public class PyUpdateProjectSdkAction extends DumbAwareAction {
  private static final int N_REPEATS = 1;
  private static final int TIMEOUT = 0; // ms
  private static final Logger LOG = Logger.getInstance(PyUpdateProjectSdkAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      for (int i = 0; i < N_REPEATS; i++) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          Sdk sdk = PythonSdkUtil.findPythonSdk(module);
          if (sdk == null) {
            LOG.info("Skipping module " + module + " as not having a Python SDK");
            continue;
          }
          PythonSdkUpdater.scheduleUpdate(sdk, project);
          TimeoutUtil.sleep(TIMEOUT);
        }
      }
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
