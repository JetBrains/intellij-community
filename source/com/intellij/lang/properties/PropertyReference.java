package com.intellij.lang.properties;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiElement;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.IncorrectOperationException;

/**
 * @author cdr
 */
class PropertyReference implements PsiReference {
  private final Property myProperty;
  private final PsiLiteralExpression myLiteralExpression;

  public PropertyReference(final Property property, final PsiLiteralExpression literalExpression) {
    myProperty = property;
    myLiteralExpression = literalExpression;
  }

  public PsiElement getElement() {
    return myLiteralExpression;
  }

  public TextRange getRangeInElement() {
    return new TextRange(1,myLiteralExpression.getTextLength()-1);
  }

  public PsiElement resolve() {
    return myProperty;
  }

  public String getCanonicalText() {
    return myProperty.getName();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof Property && Comparing.strEqual(((Property)element).getKey(), myProperty.getKey());
  }

  public Object[] getVariants() {
    return new Object[0];
  }

  public boolean isSoft() {
    return false;
  }
}
