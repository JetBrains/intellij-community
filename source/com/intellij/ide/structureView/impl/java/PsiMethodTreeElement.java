package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

public class PsiMethodTreeElement extends JavaClassTreeElementBase {
  private final PsiMethod myMethod;

  public PsiMethodTreeElement(PsiMethod method, boolean isInherited) {
    super(isInherited);
    myMethod = method;
  }

  public StructureViewTreeElement[] getChildrenBase() {
    return new StructureViewTreeElement[0];
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public PsiElement getElement() {
    return myMethod;
  }

  public String getPresentableText() {
    return PsiFormatUtil.formatMethod(
      myMethod,
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
  }

  public PsiMethod getMethod() {
    return myMethod;
  }
}
