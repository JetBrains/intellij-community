package com.intellij.ide.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;

import javax.swing.*;

public class MemberContainerCellRenderer extends PsiClassListCellRenderer{
  private final boolean myShowMethodNames;

  public MemberContainerCellRenderer(boolean showMethodNames) {
    myShowMethodNames = showMethodNames;
  }

  public String getElementText(PsiElement element) {
    String text = super.getElementText(fetchContainer(element));
    if (myShowMethodNames) {
      final int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS;
      text += "."+PsiFormatUtil.formatMethod((PsiMethod)element, PsiSubstitutor.EMPTY, options, PsiFormatUtil.SHOW_TYPE);
    }
    return text;
  }

  protected Icon getIcon(PsiElement element) {
    return super.getIcon(myShowMethodNames ? element : fetchContainer(element));
  }

  private PsiElement fetchContainer(PsiElement element){
    if (element instanceof PsiMember) {
      PsiMethod method = (PsiMethod)element;
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return method.getContainingFile();
      }
      else {
        return aClass;
      }
    }
    else {
      throw new IllegalArgumentException("unknown value: " + element);
    }
  }
}
