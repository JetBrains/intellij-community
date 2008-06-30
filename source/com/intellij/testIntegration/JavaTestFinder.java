package com.intellij.testIntegration;

import com.intellij.codeInsight.TestUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class JavaTestFinder implements TestFinder {
  public PsiClass findSourceElement(PsiElement from) {
    return PsiTreeUtil.getParentOfType(from, PsiClass.class, false);
  }

  public Collection<PsiElement> findClassesForTest(PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    List<PsiElement> result = new ArrayList<PsiElement>();
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope(getModule(element));
    PsiShortNamesCache cache = JavaPsiFacade.getInstance(element.getProject()).getShortNamesCache();
    for (String each : collectPossibleClassNames(klass.getName())) {
      for (PsiClass eachClass : cache.getClassesByName(each, scope)) {
        if (!TestUtil.isTestClass(eachClass)) {
          result.add(eachClass);
        }
      }
    }

    return result;
  }

  private List<String> collectPossibleClassNames(String testName) {
    String[] words = NameUtil.splitNameIntoWords(testName);
    List<String> result = new ArrayList<String>();

    for (int from = 0; from < words.length; from++) {
      for (int to = from; to < words.length; to++) {
        result.add(StringUtil.join(words, from, to + 1, ""));
      }
    }

    return result;
  }

  public Collection<PsiElement> findTestsForClass(PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) return Collections.emptySet();

    // todo test scope
    GlobalSearchScope scope = GlobalSearchScope.moduleTestsWithDependentsScope(getModule(element));

    Pattern pattern = Pattern.compile(".*"+ klass.getName() + ".*");

    List<PsiElement> result = new ArrayList<PsiElement>();

    PsiShortNamesCache cache = JavaPsiFacade.getInstance(element.getProject()).getShortNamesCache();
    for (String each : cache.getAllClassNames()) {
      if (pattern.matcher(each).matches()) {
        for (PsiClass eachClass : cache.getClassesByName(each, scope)) {
          if (TestUtil.isTestClass(eachClass)) result.add(eachClass);
        }
      }
    }

    return result;
  }

  private static Module getModule(PsiElement element) {
    ProjectFileIndex index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    return index.getModuleForFile(element.getContainingFile().getVirtualFile());
  }

  public boolean isTest(PsiElement element) {
    PsiClass klass = findSourceElement(element);
    return klass != null && TestUtil.isTestClass(klass);
  }
}
