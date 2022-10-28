// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;

final class PsiExternalResourceNotifier implements StartupActivity.DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    project.getMessageBus().simpleConnect().subscribe(ExternalResourceListener.TOPIC, () -> {
      if (!project.isDisposed()) {
        PsiManagerEx.getInstanceEx(project).beforeChange(true);
        DaemonCodeAnalyzer.getInstance(project).restart();
      }
    });
  }
}
