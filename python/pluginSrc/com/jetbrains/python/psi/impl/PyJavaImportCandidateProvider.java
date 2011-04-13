package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.PyImportCandidateProvider;

/**
 * @author yole
 */
public class PyJavaImportCandidateProvider implements PyImportCandidateProvider {
  @Override
  public void addImportCandidates(PsiReference reference, String name, AutoImportQuickFix quickFix) {
    final PsiElement element = reference.getElement();
    final Project project = element.getProject();
    Module module = ModuleUtil.findModuleForPsiElement(element);
    GlobalSearchScope scope = module == null ? ProjectScope.getAllScope(project) : module.getModuleWithDependenciesAndLibrariesScope(false);
    PsiShortNamesCache cache = JavaPsiFacade.getInstance(project).getShortNamesCache();
    final PsiClass[] classesByName = cache.getClassesByName(name, scope);
    for (PsiClass psiClass : classesByName) {
      final PyQualifiedName packageQName = PyQualifiedName.fromDottedString(psiClass.getQualifiedName()).removeLastComponent();
      quickFix.addImport(psiClass, psiClass.getContainingFile(), packageQName, null);
    }
  }
}
