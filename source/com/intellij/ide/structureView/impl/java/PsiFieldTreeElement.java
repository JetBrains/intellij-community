package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

public class PsiFieldTreeElement extends JavaClassTreeElementBase{
  private final PsiField myField;

  public PsiFieldTreeElement(PsiField field, boolean isInherited) {
    super(isInherited);
    myField = field;
  }

  public StructureViewTreeElement[] getChildrenBase() {
    return new StructureViewTreeElement[0];
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public PsiElement getElement() {
    return myField;
  }

  public String getPresentableText() {
    return myField.getName();
  }

  public PsiField getField() {
    return myField;
  }
}
