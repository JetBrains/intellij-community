package com.intellij.testIntegration.createTest;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.module.Module;
import com.intellij.testIntegration.CreateTestProvider;

public abstract class CreateTestBaseProvider implements CreateTestProvider {
  public boolean isLibraryAttached(Module m) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(m);
    PsiClass c = JavaPsiFacade.getInstance(m.getProject()).findClass(getMarkerClassFQName(), scope);
    return c != null;
  }

  protected abstract String getMarkerClassFQName();
}
