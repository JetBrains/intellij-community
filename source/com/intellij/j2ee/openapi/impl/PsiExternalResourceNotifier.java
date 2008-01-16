package com.intellij.j2ee.openapi.impl;

import com.intellij.j2ee.extResources.ExternalResourceListener;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
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

  public PsiExternalResourceNotifier(PsiManagerEx psiManager, ExternalResourceManagerEx externalResourceManager) {
    myPsiManager = psiManager;
    myExternalResourceManager = externalResourceManager;
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
    }
  }
}
