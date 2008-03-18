package com.intellij.javaee;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiExternalResourceNotifier implements ProjectComponent {
  private final ExternalResourceListener myExternalResourceListener;
  private PsiManagerEx myPsiManager;
  private ExternalResourceManagerEx myExternalResourceManager;
  private DaemonCodeAnalyzer myDaemonCodeAnalyzer;

  public PsiExternalResourceNotifier(PsiManagerEx psiManager, ExternalResourceManagerEx externalResourceManager,
                                     final DaemonCodeAnalyzer daemonCodeAnalyzer) {
    myPsiManager = psiManager;
    myExternalResourceManager = externalResourceManager;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
    myExternalResourceListener = new MyExternalResourceListener();
    myExternalResourceManager.addExternalResourceListener(myExternalResourceListener);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "PsiExternalResourceNotifier";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myExternalResourceManager.removeExternalResourceListener(myExternalResourceListener);
  }

  private class MyExternalResourceListener implements ExternalResourceListener {
    public void externalResourceChanged() {
      myPsiManager.physicalChange();
      myDaemonCodeAnalyzer.restart();
    }
  }
}
