package com.intellij.psi.impl.light;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightClassReferenceExpression extends LightClassReference implements PsiReferenceExpression {

  public LightClassReferenceExpression(PsiManager manager, String text, PsiClass refClass) {
    super(manager, text, refClass);
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

  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for light references");
  }

  public void processVariants(PsiScopeProcessor processor){
    throw new RuntimeException("Variants are not available for light references");
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }
}
