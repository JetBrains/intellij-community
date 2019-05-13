// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.impl.PsiManagerEx;

public class PsiExternalResourceNotifier {
  private final PsiManagerEx myPsiManager;
  private final ExternalResourceManagerEx myExternalResourceManager;
  private final DaemonCodeAnalyzer myDaemonCodeAnalyzer;

  public PsiExternalResourceNotifier(PsiManagerEx psiManager, ExternalResourceManager externalResourceManager,
                                     final DaemonCodeAnalyzer daemonCodeAnalyzer, Project project) {
    myPsiManager = psiManager;
    myExternalResourceManager = (ExternalResourceManagerEx)externalResourceManager;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
    final ExternalResourceListener myExternalResourceListener = new MyExternalResourceListener();
    myExternalResourceManager.addExternalResourceListener(myExternalResourceListener);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        myExternalResourceManager.removeExternalResourceListener(myExternalResourceListener);
      }
    });
  }

  private class MyExternalResourceListener implements ExternalResourceListener {
    @Override
    public void externalResourceChanged() {
      myPsiManager.beforeChange(true);
      myDaemonCodeAnalyzer.restart();
    }
  }
}
