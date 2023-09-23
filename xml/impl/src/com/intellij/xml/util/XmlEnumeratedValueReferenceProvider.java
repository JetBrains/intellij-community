// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiDelegateReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
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

  public static final Key<Boolean> SUPPRESS = Key.create("suppress attribute value references");

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {

    if (XmlSchemaTagsProcessor.PROCESSING_FLAG.get() != null || context.get(SUPPRESS) != null) {
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
    if (!(descriptor instanceof XmlEnumerationDescriptor enumerationDescriptor)) {
      return PsiReference.EMPTY_ARRAY;
    }

    @SuppressWarnings("unchecked") PsiElement host = getHost((T)element);
    if (host instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)host)) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (enumerationDescriptor.isFixed() || enumerationDescriptor.isEnumerated((XmlElement)element)) {
      //noinspection unchecked
      return enumerationDescriptor.getValueReferences((XmlElement)element, unquotedValue);
    }
    else if (unquotedValue.equals(enumerationDescriptor.getDefaultValue())) {  // todo case insensitive
      return ContainerUtil.map2Array(enumerationDescriptor.getValueReferences((XmlElement)element, unquotedValue), PsiReference.class,
                                     reference -> PsiDelegateReference.createSoft(reference, true));
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
        XmlText[] textElements = PsiTreeUtil.getChildrenOfType(element, XmlText.class);
        return ArrayUtil.getFirstElement(textElements);
      }
    };
  }
}
