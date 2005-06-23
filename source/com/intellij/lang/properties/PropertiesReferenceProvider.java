package com.intellij.lang.properties;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author cdr
 */
public class PropertiesReferenceProvider implements PsiReferenceProvider {
  public PsiReference[] getReferencesByElement(PsiElement element) {
    final Object value;

    if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      value = literalExpression.getValue();
    } else if (element instanceof XmlAttributeValue) {
      value = ((XmlAttributeValue)element).getValue();
    } else {
      value = null;
    }

    if (value instanceof String) {
      String text = (String)value;
      PsiReference reference = new PropertyReference(text, element);
      return new PsiReference[]{reference};
    }
    return PsiReference.EMPTY_ARRAY;
  }

  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }

}
