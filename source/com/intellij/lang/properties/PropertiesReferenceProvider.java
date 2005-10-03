package com.intellij.lang.properties;

import com.intellij.codeInsight.i18n.I18nUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttributeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * @author cdr
 */
public class PropertiesReferenceProvider implements PsiReferenceProvider {
  public PsiReference[] getReferencesByElement(PsiElement element) {
    final Object value;
    String bundleName = null;

    if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      value = literalExpression.getValue();

      final Map<String, Object> annotationParams = new HashMap<String, Object>();
      annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
      if (I18nUtil.mustBePropertyKey(literalExpression, annotationParams)) {
        final Object resourceBundleName = annotationParams.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
        if (resourceBundleName instanceof PsiLiteralExpression) {
          bundleName = ((PsiLiteralExpression)resourceBundleName).getValue().toString();
        }
      }

    } else if (element instanceof XmlAttributeValue) {
      value = ((XmlAttributeValue)element).getValue();
    } else {
      value = null;
    }

    if (value instanceof String) {
      String text = (String)value;
      PsiReference reference = new PropertyReference(text, element, bundleName);
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
