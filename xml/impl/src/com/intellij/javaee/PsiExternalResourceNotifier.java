// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.impl.PsiManagerEx;

public final class PsiExternalResourceNotifier {
  private final PsiManagerEx myPsiManager;
  private final DaemonCodeAnalyzer myDaemonCodeAnalyzer;

  public PsiExternalResourceNotifier(Project project) {
    myPsiManager = PsiManagerEx.getInstanceEx(project);

    ExternalResourceManagerEx externalResourceManager = ExternalResourceManagerEx.getInstanceEx();
    myDaemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    final ExternalResourceListener myExternalResourceListener = new MyExternalResourceListener();
    externalResourceManager.addExternalResourceListener(myExternalResourceListener);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        externalResourceManager.removeExternalResourceListener(myExternalResourceListener);
      }
    });
  }

  private final class MyExternalResourceListener implements ExternalResourceListener {
    @Override
    public void externalResourceChanged() {
      myPsiManager.beforeChange(true);
      myDaemonCodeAnalyzer.restart();
    }
  }
}
