package com.intellij.packageDependencies.ui;

import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

import java.util.Comparator;

public class DependencyNodeComparator implements Comparator<PackageDependenciesNode>{
  private final boolean mySortByType;

  public DependencyNodeComparator(final boolean sortByType) {
    mySortByType = sortByType;
  }

  public DependencyNodeComparator() {
    mySortByType = false;
  }

  public int compare(PackageDependenciesNode p1, PackageDependenciesNode p2) {
    if (p1.getWeight() != p2.getWeight()) return p1.getWeight() - p2.getWeight();
    if (mySortByType) {
      final PsiElement psiElement1 = p1.getPsiElement();
      final PsiElement psiElement2 = p2.getPsiElement();
      if (psiElement1 instanceof PsiClass && psiElement2 instanceof PsiClass) {
        return GroupByTypeComparator.getClassPosition((PsiClass)psiElement1) - GroupByTypeComparator.getClassPosition((PsiClass)psiElement2);
      }
    }
    return p1.toString().compareTo(p2.toString());
  }
}