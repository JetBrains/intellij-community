package com.intellij.lang.properties;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.i18n.I18nUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.jsp.jspJava.JspXmlTagBase;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.jsp.el.ELExpressionHolder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author cdr
 */
public class PropertiesReferenceProvider implements PsiReferenceProvider {

  private final boolean myDefaultSoft;


  public PropertiesReferenceProvider(final boolean defaultSoft) {
    myDefaultSoft = defaultSoft;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    Object value = null;
    String bundleName = null;
    boolean propertyRefWithPrefix = false;
    boolean soft = myDefaultSoft;

    if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      value = literalExpression.getValue();

      final Map<String, Object> annotationParams = new HashMap<String, Object>();
      annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
      if (I18nUtil.mustBePropertyKey(literalExpression, annotationParams)) {
        soft = false;
        final Object resourceBundleName = annotationParams.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
        if (resourceBundleName instanceof PsiExpression) {
          PsiExpression expr = (PsiExpression)resourceBundleName;
          final Object bundleValue = expr.getManager().getConstantEvaluationHelper().computeConstantExpression(expr);
          bundleName = bundleValue == null ? null : bundleValue.toString();
        }
      }
    }
    else if (element instanceof XmlAttributeValue && isNonDynamicAttribute(element)) {
      value = ((XmlAttributeValue)element).getValue();
      final XmlAttribute attribute = (XmlAttribute)element.getParent();
      if ("key".equals(attribute.getName())) {
        final XmlTag parent = attribute.getParent();
        if ("message".equals(parent.getLocalName()) && Arrays.binarySearch(XmlUtil.JSTL_FORMAT_URIS, parent.getNamespace()) >= 0) {
          propertyRefWithPrefix = true;
        }
      }
    }

    if (value instanceof String) {
      String text = (String)value;
      PsiReference reference = propertyRefWithPrefix ?
                               new PrefixBasedPropertyReference(text, element, bundleName):
                               new PropertyReference(text, element, bundleName, soft);
      return new PsiReference[]{reference};
    }
    return PsiReference.EMPTY_ARRAY;
  }

  static boolean isNonDynamicAttribute(final PsiElement element) {
    return PsiTreeUtil.getChildOfAnyType(element, ELExpressionHolder.class,JspXmlTagBase.class) == null;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }

}
