package com.intellij.ide.projectView;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

import java.util.Arrays;
import java.util.List;

public interface PsiClassChildrenSource {
  void addChildren(PsiClass psiClass, List<PsiElement> children);

  PsiClassChildrenSource NONE = new PsiClassChildrenSource() {
    public void addChildren(PsiClass psiClass, List<PsiElement> children) { }
  };

  PsiClassChildrenSource METHODS = new PsiClassChildrenSource() {
    public void addChildren(PsiClass psiClass, List<PsiElement> children) {
      children.addAll(Arrays.asList(psiClass.getMethods()));
    }
  };

  PsiClassChildrenSource FIELDS = new PsiClassChildrenSource() {
    public void addChildren(PsiClass psiClass, List<PsiElement> children) {
      children.addAll(Arrays.asList(psiClass.getFields()));
    }
  };

  PsiClassChildrenSource CLASSES = new PsiClassChildrenSource() {
    public void addChildren(PsiClass psiClass, List<PsiElement> children) {
      children.addAll(Arrays.asList(psiClass.getInnerClasses()));
    }
  };

  PsiClassChildrenSource DEFAULT_CHILDREN = new CompositePsiClasChildrenSource(new PsiClassChildrenSource[]{CLASSES, METHODS, FIELDS});
}
