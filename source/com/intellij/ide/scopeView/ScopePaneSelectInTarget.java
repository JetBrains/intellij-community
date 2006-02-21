package com.intellij.ide.scopeView;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;

/**
 * @author cdr
 */
public class ScopePaneSelectInTarget extends ProjectViewSelectInTarget {
  public ScopePaneSelectInTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.SCOPE;
  }

  public boolean canSelect(PsiFile file) {
    NamedScopesHolder scopesHolder = DependencyValidationManager.getInstance(myProject);
    NamedScope[] allScopes = scopesHolder.getScopes();
    for (NamedScope scope : allScopes) {
      PackageSet packageSet = scope.getValue();
      if (packageSet.contains(file, scopesHolder)) return true;
    }
    return false;
  }

  public String getMinorViewId() {
    return ScopeViewPane.ID;
  }

  public float getWeight() {
    return StandardTargetWeights.SCOPE_WEIGHT;
  }

  protected boolean canWorkWithCustomObjects() {
    return false;
  }

  public boolean isSubIdSelectable(String subId, VirtualFile file) {
    NamedScopesHolder scopesHolder = DependencyValidationManager.getInstance(myProject);
    NamedScope scope = scopesHolder.getScope(subId);
    PackageSet packageSet = scope.getValue();
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);

    return packageSet.contains(psiFile, scopesHolder);
  }
}
