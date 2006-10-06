package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

import java.util.Collection;
import java.util.Collections;

public class PsiFieldTreeElement extends JavaClassTreeElementBase<PsiField>{
  public PsiFieldTreeElement(PsiField field, boolean isInherited) {
    super(isInherited,field);
 }

  public Collection<StructureViewTreeElement> getChildrenBase() {
    return Collections.emptyList();
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public String getPresentableText() {
    return PsiFormatUtil.formatVariable(
      getElement(),
      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_INITIALIZER,
      PsiSubstitutor.EMPTY
    );
  }

  public PsiField getField() {
    return getElement();
  }
}
