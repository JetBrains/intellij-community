package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.*;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyJavaImportResolver implements PyImportResolver {
  @Nullable
  public PsiElement resolveImportReference(final PyElement importElement, final PyQualifiedName importText, PyQualifiedName importFrom) {
    String fqn = importText.toString();
    if (importFrom != null) {
      fqn = importFrom.toString() + "." + fqn;
    }

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(importElement.getProject());
    if (psiFacade == null) return null;
    final PsiPackage aPackage = psiFacade.findPackage(fqn);
    if (aPackage != null) {
      return aPackage;
    }

    Module sourceModule = ModuleUtil.findModuleForPsiElement(importElement);
    if (sourceModule != null) {
      final PsiClass aClass = psiFacade.findClass(fqn, sourceModule.getModuleWithDependenciesAndLibrariesScope(false));
      if (aClass != null) return aClass;
    }
    return null;
  }
}
