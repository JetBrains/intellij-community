/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.*;
import com.intellij.xml.util.XmlEnumeratedValueReferenceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public class GenericValueReferenceProvider extends PsiReferenceProvider {
  private final static Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.GenericValueReferenceProvider");

  @Override
  @NotNull
  public final PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull final ProcessingContext context) {
    final DomManagerImpl domManager = DomManagerImpl.getDomManager(psiElement.getProject());

    final DomInvocationHandler<?, ?> handler;
    if (psiElement instanceof XmlTag) {
      handler = domManager.getDomHandler((XmlTag)psiElement);
    } else if (psiElement instanceof XmlAttributeValue && psiElement.getParent() instanceof XmlAttribute) {
      handler = domManager.getDomHandler((XmlAttribute)psiElement.getParent());
    } else {
      return PsiReference.EMPTY_ARRAY;
    }

    if (handler == null || !GenericDomValue.class.isAssignableFrom(handler.getRawType())) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (psiElement instanceof XmlTag) {
      for (XmlText text : ((XmlTag)psiElement).getValue().getTextElements()) {
        if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)text)) return PsiReference.EMPTY_ARRAY;
      }
    } else {
      if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)psiElement)) return PsiReference.EMPTY_ARRAY;
    }

    final GenericDomValue domValue = (GenericDomValue)handler.getProxy();

    final Referencing referencing = handler.getAnnotation(Referencing.class);
    final Object converter;
    if (referencing == null) {
      converter = WrappingConverter.getDeepestConverter(domValue.getConverter(), domValue);
    }
    else {
      Class<? extends CustomReferenceConverter> clazz = referencing.value();
      converter = ((ConverterManagerImpl)domManager.getConverterManager()).getInstance(clazz);
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

  private static PsiReference[] createReferences(final GenericDomValue domValue,
                                                 final XmlElement psiElement,
                                                 final Object converter,
                                                 DomInvocationHandler handler,
                                                 DomManager domManager) {
    final XmlFile file = handler.getFile();
    final DomFileDescription<?> description = domManager.getDomFileDescription(file);
    if (description == null) {
      return PsiReference.EMPTY_ARRAY; // should not happen
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

    return result.toArray(new PsiReference[result.size()]);
  }

  @NotNull
  private static PsiReference[] doCreateReferences(GenericDomValue domValue, XmlElement psiElement, Object converter, ConvertContext context) {
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
