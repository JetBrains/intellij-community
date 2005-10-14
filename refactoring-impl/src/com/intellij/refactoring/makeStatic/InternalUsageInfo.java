/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 16.04.2002
 * Time: 17:09:40
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.makeStatic;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;

class InternalUsageInfo extends UsageInfo{
  private final PsiElement myReferencedElement;
  private Boolean myIsInsideAnonymous;

  InternalUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element);
    myReferencedElement = referencedElement;
    myIsInsideAnonymous = null;
    isInsideAnonymous();
  }

  public PsiElement getReferencedElement() {
    return myReferencedElement;
  }

  public boolean isInsideAnonymous() {
    if(myIsInsideAnonymous == null) {
      myIsInsideAnonymous = Boolean.valueOf(RefactoringUtil.isInsideAnonymous(getElement(), null));
    }

    return myIsInsideAnonymous.booleanValue();
  }

  public boolean isWriting() {
    return myReferencedElement instanceof PsiField &&
              getElement() instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting(((PsiReferenceExpression)getElement()));
  }
}
