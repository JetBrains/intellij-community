// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;


public class PyManagePackagesAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Module module = e.getData(PlatformCoreDataKeys.MODULE);
    final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (module != null && sdk != null) {
      new PyManagePackagesDialog(module.getProject(), sdk).show();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Module module = e.getData(PlatformCoreDataKeys.MODULE);
    e.getPresentation().setEnabled(module != null && PythonSdkUtil.findPythonSdk(module) != null);
  }
}