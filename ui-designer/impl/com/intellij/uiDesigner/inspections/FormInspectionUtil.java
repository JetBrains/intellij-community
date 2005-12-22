package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;

import javax.swing.*;

/**
 * @author yole
 */
public class FormInspectionUtil {
  private FormInspectionUtil() {
  }

  public static boolean isComponentClass(final Module module, final IComponent component,
                                         final Class<? extends JComponent> componentClass) {
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    final PsiClass aClass = psiManager.findClass(component.getComponentClassName(), scope);
    if (aClass != null) {
      final PsiClass labelClass = psiManager.findClass(componentClass.getName(), scope);
      if (labelClass != null && InheritanceUtil.isInheritorOrSelf(aClass, labelClass, true)) {
        return true;
      }
    }
    return false;
  }
}
