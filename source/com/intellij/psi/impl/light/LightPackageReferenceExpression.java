package com.intellij.psi.impl.light;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class LightPackageReferenceExpression extends LightPackageReference implements PsiReferenceExpression {
  public LightPackageReferenceExpression(PsiManager manager, PsiPackage refPackage) {
    super(manager, refPackage);
  }

  public PsiExpression getQualifierExpression(){
    return null;
  }

  public PsiElement bindToElementViaStaticImport(PsiClass aClass) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    throw new IncorrectOperationException("This method should not be called for light elements");
  }

  public PsiType getType(){
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }
}
