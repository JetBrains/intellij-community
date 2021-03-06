// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.*;
import com.intellij.xml.util.XmlEnumeratedValueReferenceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GenericValueReferenceProvider extends PsiReferenceProvider {
  private final static Logger LOG = Logger.getInstance(GenericValueReferenceProvider.class);

  @Override
  public final PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull final ProcessingContext context) {
    final DomManagerImpl domManager = DomManagerImpl.getDomManager(psiElement.getProject());

    final DomInvocationHandler handler;
    if (psiElement instanceof XmlTag) {
      handler = domManager.getDomHandler((XmlTag)psiElement);
    }
    else if (psiElement instanceof XmlAttributeValue && psiElement.getParent() instanceof XmlAttribute) {
      handler = domManager.getDomHandler((XmlAttribute)psiElement.getParent());
    }
    else {
      return PsiReference.EMPTY_ARRAY;
    }

    if (handler == null || !GenericDomValue.class.isAssignableFrom(handler.getRawType())) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (psiElement instanceof XmlTag) {
      for (XmlText text : ((XmlTag)psiElement).getValue().getTextElements()) {
        if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)text)) {
          return PsiReference.EMPTY_ARRAY;
        }
      }
    }
    else if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)psiElement)) {
      return PsiReference.EMPTY_ARRAY;
    }

    final GenericDomValue<?> domValue = (GenericDomValue<?>)handler.getProxy();
    final Referencing referencing = handler.getAnnotation(Referencing.class);
    final Object converter;
    if (referencing == null) {
      converter = WrappingConverter.getDeepestConverter(domValue.getConverter(), domValue);
    }
    else {
      converter = ConverterManagerImpl.getOrCreateConverterInstance(referencing.value());
    }
    PsiReference[] references = createReferences(domValue, (XmlElement)psiElement, converter, handler, domManager);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (PsiReference reference : references) {
        if (!reference.isSoft()) {
          LOG.error("dom reference should be soft: " + reference + " (created by " + converter + ")");
        }
      }
    }
    if (references.length > 0) {
      if (converter instanceof EnumConverter && !((EnumConverter)converter).isExhaustive()) {
        // will be handled by core XML
        return PsiReference.EMPTY_ARRAY;
      }
      context.put(XmlEnumeratedValueReferenceProvider.SUPPRESS, Boolean.TRUE);
    }
    return references;
  }

  private static PsiReference[] createReferences(GenericDomValue<?> domValue,
                                                 XmlElement psiElement,
                                                 Object converter,
                                                 DomInvocationHandler handler,
                                                 DomManager domManager) {
    final XmlFile file = handler.getFile();
    final DomFileDescription<?> description = domManager.getDomFileDescription(file);
    if (description == null) {
      // should not happen
      return PsiReference.EMPTY_ARRAY;
    }

    List<PsiReference> result = new ArrayList<>();

    ConvertContext context = ConvertContextFactory.createConvertContext(domValue);
    final List<DomReferenceInjector> injectors = description.getReferenceInjectors();
    if (!injectors.isEmpty()) {
      String unresolvedText = ElementManipulators.getValueText(psiElement);
      for (DomReferenceInjector each : injectors) {
        Collections.addAll(result, each.inject(unresolvedText, psiElement, context));
      }
    }

    Collections.addAll(result, doCreateReferences(domValue, psiElement, converter, context));
    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  private static PsiReference @NotNull [] doCreateReferences(GenericDomValue domValue, XmlElement psiElement, Object converter, ConvertContext context) {
    if (converter instanceof CustomReferenceConverter) {
      //noinspection unchecked
      final PsiReference[] references = ((CustomReferenceConverter)converter).createReferences(domValue, psiElement, context);
      if (references.length != 0 || !(converter instanceof ResolvingConverter)) {
        return references;
      }
    }

    if (converter instanceof ResolvingConverter) {
      //noinspection unchecked
      return new PsiReference[]{new GenericDomValueReference(domValue)};
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
