package com.intellij.testIntegration;

import com.intellij.codeInsight.TestUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collection;
import java.util.Collections;

public class JavaTestFinder implements TestFinder {
  public Collection<PsiElement> findClassesForTest(PsiElement element) {
    PsiClass klass = getPsiClass(element);
    if (klass == null) return Collections.emptySet();

    String name = klass.getName();
    if (name.endsWith("Test")) name = name.substring(0, name.length() - 4);

    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope(getModule(element));
    return (Collection)JavaShortClassNameIndex.getInstance().get(name, element.getProject(), scope);
  }

  public Collection<PsiElement> findTestsForClass(PsiElement element) {
    PsiClass klass = getPsiClass(element);
    if (klass == null) return Collections.emptySet();

    String name = klass.getName() + "Test";
    GlobalSearchScope scope = GlobalSearchScope.moduleTestsWithDependentsScope(getModule(element));

    return (Collection)JavaShortClassNameIndex.getInstance().get(name, element.getProject(), scope);
  }

  private static Module getModule(PsiElement element) {
    ProjectFileIndex index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    return index.getModuleForFile(element.getContainingFile().getVirtualFile());
  }

  public boolean isTest(PsiElement element) {
    PsiClass klass = getPsiClass(element);
    return klass != null && TestUtil.isTestClass(klass);
  }

  private PsiClass getPsiClass(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
  }
}
