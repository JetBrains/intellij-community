/*
 * User: anna
 * Date: 21-Jan-2008
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.ClassSmartPointerNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class PsiClassFavoriteNodeProvider extends FavoriteNodeProvider {
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
        if (element instanceof PsiClass) {
          result.add(new ClassSmartPointerNode(project, element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  @Override
  public AbstractTreeNode createNode(final Project project, final Object element, final ViewSettings viewSettings) {
    if (element instanceof PsiClass) {
      return new ClassSmartPointerNode(project, element, viewSettings);
    }
    return super.createNode(project, element, viewSettings);
  }

  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    if (element instanceof PsiClass) {
      final PsiFile file = ((PsiClass)element).getContainingFile();
      if (file != null && file.getVirtualFile() == vFile) return true;
    }
    return false;
  }

  public int getElementWeight(final Object value, final boolean isSortByType) {
     if (value instanceof PsiClass){
      return isSortByType ? GroupByTypeComparator.getClassPosition((PsiClass)value) : 3;
    }

    return -1;
  }

  public String getElementLocation(final Object element) {
    if (element instanceof PsiClass) {
      return ClassPresentationUtil.getNameForClass((PsiClass)element, true);
    }
    return null;
  }

  public boolean isInvalidElement(final Object element) {
    return element instanceof PsiClass && !((PsiClass)element).isValid();
  }

  @NotNull
  public String getFavoriteTypeId() {
    return "class";
  }

  public String getElementUrl(final Object element) {
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      return aClass.getQualifiedName();
    }
    return null;
  }

  public String getElementModuleName(final Object element) {
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      Module module = ModuleUtil.findModuleForPsiElement(aClass);
      return module != null ? module.getName() : null;
    }
    return null;
  }

  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    GlobalSearchScope scope = null;
    if (moduleName != null) {
      final Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
      if (module != null) {
        scope = GlobalSearchScope.moduleScope(module);
      }
    }
    if (scope == null) {
      scope = GlobalSearchScope.allScope(project);
    }
    final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(url, scope);
    if (aClass == null) return null;
    return new Object[]{aClass};
  }
}