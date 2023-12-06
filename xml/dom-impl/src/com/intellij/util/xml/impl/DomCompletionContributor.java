// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.getters.XmlAttributeValueGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class DomCompletionContributor extends CompletionContributor{
  private final GenericValueReferenceProvider myProvider = new GenericValueReferenceProvider();

  @Override
  public void fillCompletionVariants(final @NotNull CompletionParameters parameters, final @NotNull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) return;

    if (domKnowsBetter(parameters, result)) {
      result.stopHere();
    }
  }

  private boolean domKnowsBetter(final CompletionParameters parameters, final CompletionResultSet result) {
    final XmlAttributeValue element = PsiTreeUtil.getParentOfType(parameters.getPosition(), XmlAttributeValue.class);
    if (element == null) {
      return false;
    }

    if (isSchemaEnumerated(element)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (parent instanceof XmlAttribute) {
      XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
      if (descriptor != null && descriptor.getDefaultValue() != null) {
        final PsiReference[] references = myProvider.getReferencesByElement(element, new ProcessingContext());
        if (references.length > 0) {
          return LegacyCompletionContributor.completeReference(parameters, result);
        }
      }
    }
    return false;
  }

  public static boolean isSchemaEnumerated(final PsiElement element) {
    if (element instanceof XmlTag) {
      final XmlTag simpleContent = XmlUtil.getSchemaSimpleContent((XmlTag)element);
      if (simpleContent != null && XmlUtil.collectEnumerationValues(simpleContent, new HashSet<>())) {
        return true;
      }                  
    }
    if (element instanceof XmlAttributeValue) {
      final PsiElement parent = element.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor != null && descriptor.isEnumerated()) {
          return true;
        }

        String[] enumeratedValues = XmlAttributeValueGetter.getEnumeratedValues((XmlAttribute)parent);
        if (enumeratedValues.length > 0) {
          String value = descriptor == null ? null : descriptor.getDefaultValue();
          if (value == null || enumeratedValues.length != 1 || !value.equals(enumeratedValues[0])) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
