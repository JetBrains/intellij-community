/*
 * User: anna
 * Date: 21-Jan-2008
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.codeInspection.reference.RefMethodImpl;
import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.MethodSmartPointerNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class PsiMethodFavoriteNodeProvider extends FavoriteNodeProvider {
  public Collection<AbstractTreeNode> getFavoriteNodes(final DataContext context, final ViewSettings viewSettings) {
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    if (project == null) return null;
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context);
    if (elements == null) {
      final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(context);
      if (element != null) {
        elements = new PsiElement[]{element};
      }
    }
    if (elements != null) {
      final Collection<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      for (PsiElement element : elements) {
        if (element instanceof PsiMethod) {
          result.add(new MethodSmartPointerNode(project, element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    return false;
  }

  public int getElementWeight(final Object value, final boolean isSortByType) {
    if (value instanceof PsiMethod){
      return 5;
    }
    return -1;
  }

  public String getElementLocation(final Object element) {
    if (element instanceof PsiMethod) {
      final PsiClass parent = ((PsiMethod)element).getContainingClass();
      if (parent != null) {
        return ClassPresentationUtil.getNameForClass(parent, true);
      }
    }
    return null;
  }

  public boolean isInvalidElement(final Object element) {
    return element instanceof PsiMethod && !((PsiMethod)element).isValid();
  }

  @NotNull
  public String getFavoriteTypeId() {
    return "method";
  }

  public String getElementUrl(final Object element) {
     if (element instanceof PsiMethod) {
      PsiMethod aMethod = (PsiMethod)element;
      return PsiFormatUtil.getExternalName(aMethod);
    }
    return null;
  }

  public String getElementModuleName(final Object element) {
     if (element instanceof PsiMethod) {
      PsiMethod aMethod = (PsiMethod)element;
      Module module = ModuleUtil.findModuleForPsiElement(aMethod);
      return module != null ? module.getName() : null;
    }
    return null;
  }

  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    final PsiMethod method = RefMethodImpl.findPsiMethod(PsiManager.getInstance(project), url);
    if (method == null) return null;
    return new Object[]{method};
  }
}