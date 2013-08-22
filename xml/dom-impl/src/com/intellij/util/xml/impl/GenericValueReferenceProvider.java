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

import com.intellij.javaee.web.PsiReferenceConverter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ReflectionCache;
import com.intellij.util.xml.*;
import com.intellij.xml.util.XmlEnumeratedValueReferenceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class GenericValueReferenceProvider extends PsiReferenceProvider {

  private final static Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.GenericValueReferenceProvider");

  private final Map<Class, PsiReferenceFactory> myProviders = new HashMap<Class, PsiReferenceFactory>();

  public void addReferenceProviderForClass(Class clazz, PsiReferenceFactory provider) {
    myProviders.put(clazz, provider);
  }

  @NotNull
  public final PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull final ProcessingContext context) {
    final DomManager domManager = DomManager.getDomManager(psiElement.getProject());

    final DomElement domElement;
    if (psiElement instanceof XmlTag) {
      domElement = domManager.getDomElement((XmlTag)psiElement);
    } else if (psiElement instanceof XmlAttributeValue && psiElement.getParent() instanceof XmlAttribute) {
      domElement = domManager.getDomElement((XmlAttribute)psiElement.getParent());
    } else {
      return PsiReference.EMPTY_ARRAY;
    }

    if (!(domElement instanceof GenericDomValue)) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (psiElement instanceof XmlTag) {
      for (XmlText text : ((XmlTag)psiElement).getValue().getTextElements()) {
        if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)text)) return PsiReference.EMPTY_ARRAY;
      }
    } else {
      if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)psiElement)) return PsiReference.EMPTY_ARRAY;
    }

    final GenericDomValue domValue = (GenericDomValue)domElement;

    final Referencing referencing = domValue.getAnnotation(Referencing.class);
    final Object converter;
    if (referencing == null) {
      converter = WrappingConverter.getDeepestConverter(domValue.getConverter(), domValue);
    }
    else {
      Class<? extends CustomReferenceConverter> clazz = referencing.value();
      converter = ((ConverterManagerImpl)domManager.getConverterManager()).getInstance(clazz);
    }
    PsiReference[] references = createReferences(domValue, (XmlElement)psiElement, converter);
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

  private static ConvertContext createConvertContext(final PsiElement psiElement, final GenericDomValue domValue) {
    return ConvertContextFactory.createConvertContext(domValue);
  }

  @Nullable
  private static DomInvocationHandler getInvocationHandler(final GenericDomValue domValue) {
    return DomManagerImpl.getDomInvocationHandler(domValue);
  }

  private PsiReference[] createReferences(final GenericDomValue domValue, final XmlElement psiElement, final Object converter) {
    ConvertContext context = createConvertContext(psiElement, domValue);

    List<PsiReference> result = new ArrayList<PsiReference>();
    String unresolvedText = ElementManipulators.getValueText(psiElement);

    for (DomReferenceInjector each : DomUtil.getFileElement(domValue).getFileDescription().getReferenceInjectors()) {
      Collections.addAll(result, each.inject(unresolvedText, psiElement, context));
    }

    Collections.addAll(result, doCreateReferences(domValue, psiElement, converter, context));

    return result.toArray(new PsiReference[result.size()]);
  }

  @NotNull
  private PsiReference[] doCreateReferences(GenericDomValue domValue, XmlElement psiElement, Object converter, ConvertContext context) {
    if (converter instanceof CustomReferenceConverter) {
      final PsiReference[] references =
        ((CustomReferenceConverter)converter).createReferences(domValue, psiElement, context);

      if (references.length == 0 && converter instanceof ResolvingConverter) {
        return new PsiReference[]{new GenericDomValueReference(domValue)};
      } else {
        return references;
      }
    }
    if (converter instanceof PsiReferenceConverter) {
      return ((PsiReferenceConverter)converter).createReferences(psiElement, true);
    }
    if (converter instanceof ResolvingConverter) {
      return new PsiReference[]{new GenericDomValueReference(domValue)};
    }

    final DomInvocationHandler invocationHandler = getInvocationHandler(domValue);
    assert invocationHandler != null;
    final Class clazz = DomUtil.getGenericValueParameter(invocationHandler.getDomElementType());
    if (clazz == null) return PsiReference.EMPTY_ARRAY;

    if (ReflectionCache.isAssignable(Integer.class, clazz)) {
      return new PsiReference[]{new GenericDomValueReference<Integer>((GenericDomValue<Integer>)domValue) {
        @NotNull
        public Object[] getVariants() {
          return new Object[]{"0"};
        }
      }};
    }
    if (ReflectionCache.isAssignable(String.class, clazz)) {
      return PsiReference.EMPTY_ARRAY;
    }
    PsiReferenceFactory provider = myProviders.get(clazz);
    if (provider != null) {
      return provider.getReferencesByElement(psiElement);
    }

    return PsiReference.EMPTY_ARRAY;
  }

}
