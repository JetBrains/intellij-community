package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiField;

public class PsiFieldTreeElement extends JavaClassTreeElementBase<PsiField>{
  public PsiFieldTreeElement(PsiField field, boolean isInherited) {
    super(isInherited,field);
 }

  public StructureViewTreeElement[] getChildrenBase() {
    return new StructureViewTreeElement[0];
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public String getPresentableText() {
    return getElement().getName();
  }

  public PsiField getField() {
    return getElement();
  }
}
