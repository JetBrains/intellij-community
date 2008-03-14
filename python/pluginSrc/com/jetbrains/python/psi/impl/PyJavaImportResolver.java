package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.*;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyJavaImportResolver implements PyImportResolver {
  @Nullable
  public PsiElement resolveImportReference(final PyReferenceExpression importRef, PsiElement importFrom) {
    String fqn = importRef.getText();
    if (importFrom instanceof PsiDirectory) {
      PsiPackage fromPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory) importFrom);
      if (fromPackage != null) {
        importFrom = fromPackage;
      }
    }
    if (importFrom instanceof PsiPackage) {
      fqn = ((PsiPackage) importFrom).getQualifiedName() + "." + fqn;
    }

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(importRef.getProject());
    if (psiFacade == null) return null;
    final PsiPackage aPackage = psiFacade.findPackage(fqn);
    if (aPackage != null) {
      return aPackage;
    }

    Module sourceModule = ModuleUtil.findModuleForPsiElement(importRef);
    if (sourceModule != null) {
      final PsiClass aClass = psiFacade.findClass(fqn, sourceModule.getModuleWithDependenciesAndLibrariesScope(false));
      if (aClass != null) return aClass;
    }
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
