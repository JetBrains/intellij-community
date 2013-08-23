/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import com.intellij.xml.impl.schema.XmlSchemaTagsProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 15.08.13
 */
public class XmlEnumeratedValueReferenceProvider extends PsiReferenceProvider {

  public final static Key<Boolean> SUPPRESS = Key.create("suppress attribute value references");

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {

    if (XmlSchemaTagsProcessor.PROCESSING_FLAG.get() != null || context.get(SUPPRESS) != null) {
      return PsiReference.EMPTY_ARRAY;
    }
    XmlAttributeValue value = (XmlAttributeValue)element;
    if (value instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)value)) {
      return PsiReference.EMPTY_ARRAY;
    }
    String unquotedValue = value.getValue();
    if (unquotedValue == null || XmlHighlightVisitor.skipValidation(value) || !XmlUtil.isSimpleXmlAttributeValue(unquotedValue, value)) {
      return PsiReference.EMPTY_ARRAY;
    }
    PsiElement parent = value.getParent();
    if (parent instanceof XmlAttribute) {
      final XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
      if (descriptor instanceof XmlEnumerationDescriptor &&
          (descriptor.isFixed() || descriptor.isEnumerated() || unquotedValue.equals(descriptor.getDefaultValue()))) { // todo case insensitive
        return ((XmlEnumerationDescriptor)descriptor).getValueReferences(value);
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
