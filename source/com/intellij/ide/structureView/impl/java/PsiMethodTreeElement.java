package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

public class PsiMethodTreeElement extends JavaClassTreeElementBase<PsiMethod> {
  public PsiMethodTreeElement(PsiMethod method, boolean isInherited) {
    super(isInherited,method);
  }

  public StructureViewTreeElement[] getChildrenBase() {
    return new StructureViewTreeElement[0];
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public String getPresentableText() {
    return PsiFormatUtil.formatMethod(
      getElement(),
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
  }

  public PsiMethod getMethod() {
    return getElement();
  }
}
