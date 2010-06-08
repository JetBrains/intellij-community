/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.HashSet;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;

/**
 * @author peter
 */
public class DomCompletionContributor extends CompletionContributor{
  private final GenericValueReferenceProvider myProvider = new GenericValueReferenceProvider();

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) return;

    if (domKnowsBetter(parameters, result)) {
      result.stopHere();
    }
  }

  private boolean domKnowsBetter(final CompletionParameters parameters, final CompletionResultSet result) {
    final PsiElement element = PsiTreeUtil.getParentOfType(parameters.getPosition(), XmlTag.class, XmlAttributeValue.class);
    return element != null && ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        if (isSchemaEnumerated(element)) {
          return false;
        }
        final PsiReference[] references = myProvider.getReferencesByElement(element, new ProcessingContext());
        if (references.length > 0) {
          return LegacyCompletionContributor.completeReference(parameters, result, new XmlCompletionData());
        }
        return false;
      }
    }).booleanValue();
  }

  public static boolean isSchemaEnumerated(final PsiElement element) {
    if (element instanceof XmlTag) {
      final XmlTag simpleContent = XmlUtil.getSchemaSimpleContent((XmlTag)element);
      if (simpleContent != null && XmlUtil.collectEnumerationValues(simpleContent, new HashSet<String>())) {
        return true;
      }                  
    }
    if (element instanceof XmlAttributeValue) {
      final PsiElement parent = element.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor != null && descriptor.isEnumerated()) return true;
      }
    }
    return false;
  }
}
