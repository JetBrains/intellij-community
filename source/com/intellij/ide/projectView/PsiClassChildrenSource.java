package com.intellij.ide.projectView;

import com.intellij.aspects.psi.*;
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

  PsiClassChildrenSource ASPECT_CHILDREN = new PsiClassChildrenSource() {
    public void addChildren(PsiClass psiClass, List<PsiElement> children) {
      if (!(psiClass instanceof PsiAspect)) return;
      PsiAspect aspect = (PsiAspect)psiClass;
      PsiAdvice[] advices = aspect.getAdvices();
      PsiIntertypeDeclaration[] intertypeDeclarations = aspect.getIntertypeDeclarations();
      PsiIntroduction[] introductions = aspect.getIntroductions();
      PsiPointcutDef[] pointcutDefs = aspect.getPointcutDefs();
      children.addAll(Arrays.asList(advices));
      children.addAll(Arrays.asList(intertypeDeclarations));
      children.addAll(Arrays.asList(introductions));
      children.addAll(Arrays.asList(pointcutDefs));
    }
  };

  PsiClassChildrenSource DEFAULT_CHILDREN = new CompositePsiClasChildrenSource(
      new PsiClassChildrenSource[]{CLASSES,
                                   METHODS,
                                   FIELDS,
                                   ASPECT_CHILDREN});  
}
