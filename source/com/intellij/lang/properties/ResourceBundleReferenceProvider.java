package com.intellij.lang.properties;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;

/**
 * @author yole
 */
public class ResourceBundleReferenceProvider implements PsiReferenceProvider {
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression expr = (PsiLiteralExpression) element;
      final Object value = expr.getValue();
      if (value != null) {
        ResourceBundleReference reference = new ResourceBundleReference(expr, value.toString());
        return new PsiReference[] { reference };
      }
    }
    return null;
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
