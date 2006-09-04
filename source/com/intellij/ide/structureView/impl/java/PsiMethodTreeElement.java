package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;

import java.util.ArrayList;
import java.util.Collection;

public class PsiMethodTreeElement extends JavaClassTreeElementBase<PsiMethod> {
  public PsiMethodTreeElement(PsiMethod method, boolean isInherited) {
    super(isInherited,method);
  }

  public Collection<StructureViewTreeElement> getChildrenBase() {
    final ArrayList<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
    getElement().accept(new PsiRecursiveElementVisitor(){
      public void visitClass(PsiClass aClass) {
        if (!(aClass instanceof PsiAnonymousClass) && !(aClass instanceof PsiTypeParameter)) {
          result.add(new JavaClassTreeElement(aClass, isInherited()));
        }

      }
    });
    return result;
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


  public TextAttributesKey getTextAttributesKey() {
    if (isInherited()) return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
    return super.getTextAttributesKey();
  }

  public PsiMethod getMethod() {
    return getElement();
  }
}
