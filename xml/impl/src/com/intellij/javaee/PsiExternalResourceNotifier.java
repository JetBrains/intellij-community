package com.intellij.javaee;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiExternalResourceNotifier extends AbstractProjectComponent {
  private final PsiManagerEx myPsiManager;
  private final ExternalResourceManagerEx myExternalResourceManager;
  private final DaemonCodeAnalyzer myDaemonCodeAnalyzer;

  public PsiExternalResourceNotifier(PsiManagerEx psiManager, ExternalResourceManager externalResourceManager,
                                     final DaemonCodeAnalyzer daemonCodeAnalyzer, Project project) {
    super(project);
    myPsiManager = psiManager;
    myExternalResourceManager = (ExternalResourceManagerEx)externalResourceManager;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
    final ExternalResourceListener myExternalResourceListener = new MyExternalResourceListener();
    myExternalResourceManager.addExternalResourceListener(myExternalResourceListener);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        myExternalResourceManager.removeExternalResourceListener(myExternalResourceListener);
      }
    });
  }

  @NotNull
  public String getComponentName() {
    return "PsiExternalResourceNotifier";
  }

  private class MyExternalResourceListener implements ExternalResourceListener {
    public void externalResourceChanged() {
      myPsiManager.physicalChange();
      myDaemonCodeAnalyzer.restart();
    }
  }
}
