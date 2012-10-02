package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.daemon.impl.actions.AbstractSuppressByNoInspectionCommentFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElement;

/**
 * @author yole
 */
public class PySuppressInspectionFix extends AbstractSuppressByNoInspectionCommentFix {
  private final Class<? extends PyElement> myContainerClass;

  public PySuppressInspectionFix(final String ID, final String text, final Class<? extends PyElement> containerClass) {
    super(ID, false);
    setText(text);
    myContainerClass = containerClass;
  }

  @Override
  protected PsiElement getContainer(PsiElement context) {
    return PsiTreeUtil.getParentOfType(context, myContainerClass);
  }
}
