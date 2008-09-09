package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class JavaCallHierarchyProvider implements HierarchyProvider {
  public PsiElement getTarget(final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
  }

  public HierarchyBrowser createHierarchyBrowser(final PsiElement target) {
    return new CallHierarchyBrowser(target.getProject(), (PsiMethod) target);
  }

  public void browserActivated(final HierarchyBrowser hierarchyBrowser) {
    ((CallHierarchyBrowser) hierarchyBrowser).changeView(CallerMethodsTreeStructure.TYPE);
  }
}
