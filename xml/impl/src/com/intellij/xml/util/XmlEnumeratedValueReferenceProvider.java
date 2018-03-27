/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiDelegateReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import com.intellij.xml.impl.schema.XmlSchemaTagsProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlEnumeratedValueReferenceProvider<T extends PsiElement> extends PsiReferenceProvider {

  public final static Key<Boolean> SUPPRESS = Key.create("suppress attribute value references");

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {

    if (XmlSchemaTagsProcessor.PROCESSING_FLAG.get() != null || context.get(SUPPRESS) != null) {
      return PsiReference.EMPTY_ARRAY;
    }
    
    @SuppressWarnings("unchecked") PsiElement host = getHost((T)element);
    if (host instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)host)) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (XmlHighlightVisitor.skipValidation(element)) {
      return PsiReference.EMPTY_ARRAY;
    }

    String unquotedValue = ElementManipulators.getValueText(element);
    if (!XmlUtil.isSimpleValue(unquotedValue, element)) {
      return PsiReference.EMPTY_ARRAY;
    }

    @SuppressWarnings("unchecked") final Object descriptor = getDescriptor((T)element);
    if (descriptor instanceof XmlEnumerationDescriptor) {
      XmlEnumerationDescriptor enumerationDescriptor = (XmlEnumerationDescriptor)descriptor;

      if (enumerationDescriptor.isFixed() || enumerationDescriptor.isEnumerated((XmlElement)element)) {
        //noinspection unchecked
        return enumerationDescriptor.getValueReferences((XmlElement)element, unquotedValue);
      }
      else if (unquotedValue.equals(enumerationDescriptor.getDefaultValue())) {  // todo case insensitive
        return ContainerUtil.map2Array(enumerationDescriptor.getValueReferences((XmlElement)element, unquotedValue), PsiReference.class,
                                       reference -> PsiDelegateReference.createSoft(reference, true));
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  protected PsiElement getHost(T element) {
    return element;
  }

  protected Object getDescriptor(T element) {
    PsiElement parent = element.getParent();
    return parent instanceof XmlAttribute ? ((XmlAttribute)parent).getDescriptor() : null;
  }

  public static XmlEnumeratedValueReferenceProvider forTags() {
    return new XmlEnumeratedValueReferenceProvider<XmlTag>() {

      @Override
      protected Object getDescriptor(XmlTag element) {
        return element.getDescriptor();
      }

      @Override
      protected PsiElement getHost(XmlTag element) {
        XmlText[] textElements = element.getValue().getTextElements();
        return ArrayUtil.getFirstElement(textElements);
      }
    };
  }
}
