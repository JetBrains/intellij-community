// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyJavaImportResolver implements PyImportResolver {
  @Override
  @Nullable
  public PsiElement resolveImportReference(QualifiedName name, PyQualifiedNameResolveContext context, boolean withRoots) {
    String fqn = name.toString();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(context.getProject());
    final PsiPackage aPackage = psiFacade.findPackage(fqn);
    if (aPackage != null) {
      return aPackage;
    }

    Module module = context.getModule();
    if (module != null) {
      final PsiClass aClass = psiFacade.findClass(fqn, module.getModuleWithDependenciesAndLibrariesScope(false));
      if (aClass != null) return aClass;
    }
    return null;
  }
}
