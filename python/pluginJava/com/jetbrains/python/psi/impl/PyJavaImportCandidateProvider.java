// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.PyImportCandidateProvider;


public final class PyJavaImportCandidateProvider implements PyImportCandidateProvider {
  @Override
  public void addImportCandidates(PsiReference reference, String name, AutoImportQuickFix quickFix) {
    final PsiElement element = reference.getElement();
    final Project project = element.getProject();
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    GlobalSearchScope scope = module == null ? ProjectScope.getAllScope(project) : module.getModuleWithDependenciesAndLibrariesScope(false);
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    final PsiClass[] classesByName = cache.getClassesByName(name, scope);
    for (PsiClass psiClass : classesByName) {
      final String qualifiedName = psiClass.getQualifiedName();
      if (qualifiedName == null) {
        continue;
      }
      final QualifiedName packageQName = QualifiedName.fromDottedString(qualifiedName).removeLastComponent();
      quickFix.addImport(psiClass, psiClass.getContainingFile(), packageQName);
    }
  }
}
