package com.intellij.testIntegration;

import com.intellij.codeInsight.TestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;

import java.util.Collection;
import java.util.Collections;

public class JavaTestFinder implements TestFinder {
  public Collection<PsiElement> findClassesForTest(PsiElement element) {
    if (!(element instanceof PsiClass)) return Collections.emptySet();
    String name = ((PsiClass)element).getName();
    if (name.endsWith("Test")) name = name.substring(0, name.length() - 4);
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope(getModule(element));
    return (Collection)JavaShortClassNameIndex.getInstance().get(name, element.getProject(), scope);
  }

  public Collection<PsiElement> findTestsForClass(PsiElement element) {
    if (!(element instanceof PsiClass)) return Collections.emptySet();
    String name = ((PsiClass)element).getName() + "Test";
    GlobalSearchScope scope = GlobalSearchScope.moduleTestsWithDependentsScope(getModule(element));
    return (Collection)JavaShortClassNameIndex.getInstance().get(name, element.getProject(), scope);
  }

  private static Module getModule(PsiElement element) {
    ProjectFileIndex index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    return index.getModuleForFile(element.getContainingFile().getVirtualFile());
  }

  public boolean isTest(PsiElement element) {
    return element instanceof PsiClass && TestUtil.isTestClass((PsiClass)element);
  }
}
