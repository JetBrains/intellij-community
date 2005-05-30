package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;

import java.util.ArrayList;

public class PsiMethodTreeElement extends JavaClassTreeElementBase<PsiMethod> {
  public PsiMethodTreeElement(PsiMethod method, boolean isInherited) {
    super(isInherited,method);
  }

  public StructureViewTreeElement[] getChildrenBase() {
    final ArrayList<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
    getElement().accept(new PsiRecursiveElementVisitor(){
      public void visitClass(PsiClass aClass) {
        if (!(aClass instanceof PsiAnonymousClass)) {
          result.add(new JavaClassTreeElement(aClass, isInherited()));
        }
        
      }
    });
    return result.toArray(new StructureViewTreeElement[result.size()]);
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
