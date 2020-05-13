// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;

final class PsiExternalResourceNotifier {
  PsiExternalResourceNotifier(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(ExternalResourceListener.TOPIC, () -> {
      if (!project.isDisposed()) {
        PsiManagerEx.getInstanceEx(project).beforeChange(true);
        DaemonCodeAnalyzer.getInstance(project).restart();
      }
    });
  }
}
