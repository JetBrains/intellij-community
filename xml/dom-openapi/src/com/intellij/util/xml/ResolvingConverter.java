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
package com.intellij.util.xml;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolvingHint;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * If converter extends this class, the corresponding XML {@link com.intellij.psi.PsiReference}
 * will take completion variants from {@link #getVariants(ConvertContext)} method.
 */
public abstract class ResolvingConverter<T> extends Converter<T> implements ResolvingHint {

  @InspectionMessage
  @Override
  public String getErrorMessage(@Nullable String s, final ConvertContext context) {
    return AnalysisBundle.message("error.cannot.resolve.default.message", s);
  }

  /**
   * @param context context
   * @return reference completion variants
   */
  @NotNull
  public abstract Collection<? extends T> getVariants(final ConvertContext context);

  /**
   * @return additional reference variants. They won't resolve to anywhere, but won't be highlighted as errors.
   * They will also appear in the completion dropdown.
   */
  @NotNull
  public Set<String> getAdditionalVariants(@NotNull final ConvertContext context) {
    return Collections.emptySet();
  }

  /**
   * Delegate from {@link com.intellij.psi.PsiReference#handleElementRename(String)}
   * @param genericValue generic value
   * @param context context
   * @param newElementName new element name
   */
  public void handleElementRename(final GenericDomValue<T> genericValue, final ConvertContext context,
                                  final String newElementName) {
    genericValue.setStringValue(newElementName);
  }

  /**
   * Delegate from {@link com.intellij.psi.PsiReference#bindToElement(PsiElement)}
   * @param genericValue generic value
   * @param context context
   * @param newTarget new target
   */
  public void bindReference(final GenericDomValue<T> genericValue, final ConvertContext context, final PsiElement newTarget) {
    if (newTarget instanceof XmlTag) {
      DomElement domElement = genericValue.getManager().getDomElement((XmlTag) newTarget);
      if (domElement != null) {
        genericValue.setStringValue(ElementPresentationManager.getElementName(domElement));
      }
    }
  }

  /**
   * @param resolvedValue {@link #fromString(String, ConvertContext)} result
   * @return the PSI element to which the {@link com.intellij.psi.PsiReference} will resolve
   */
  @Nullable
  public PsiElement getPsiElement(@Nullable T resolvedValue) {
    if (resolvedValue instanceof PsiElement) {
      return (PsiElement)resolvedValue;
    }
    if (resolvedValue instanceof DomElement) {
      return ((DomElement)resolvedValue).getXmlElement();
    }
    return null;
  }

  /**
   * Delegate from {@link com.intellij.psi.PsiReference#isReferenceTo(PsiElement)}
   * @param element element
   * @param stringValue string value
   * @param resolveResult resolve result
   * @param context context
   * @return is reference to?
   */
  public boolean isReferenceTo(@NotNull PsiElement element, final String stringValue, @Nullable T resolveResult,
                               final ConvertContext context) {
    return resolveResult != null && element.getManager().areElementsEquivalent(element, getPsiElement(resolveResult));
  }

  @Override
  public boolean canResolveTo(Class<? extends PsiElement> elementClass) {
    return true;
  }

  /**
   * Delegate from {@link com.intellij.psi.PsiReference#resolve()}
   * @param o {@link #fromString(String, ConvertContext)} result
   * @param context context
   * @return PSI element to resolve to. By default calls {@link #getPsiElement(Object)} method
   */
  @Nullable
  public PsiElement resolve(final T o, final ConvertContext context) {
    final PsiElement psiElement = getPsiElement(o);
    return psiElement == null && o != null ? DomUtil.getValueElement((GenericDomValue)context.getInvocationElement()) : psiElement;
  }

  /**
   * @param context context
   * @return LocalQuickFix'es to correct non-resolved value (e.g. 'create from usage')
   */
  public LocalQuickFix[] getQuickFixes(final ConvertContext context) {
    return LocalQuickFix.EMPTY_ARRAY;
  }

  /**
   * Override to provide custom lookup elements in completion.
   * <p/>
   * Default is {@code null} which will create lookup via
   * {@link ElementPresentationManager#createVariant(Object, String, PsiElement)}.
   *
   * @param t DOM to create lookup element for.
   * @return Lookup element.
   */
  @Nullable
  public LookupElement createLookupElement(T t) {
    return null;
  }

  /**
   * Adds {@link #getVariants(ConvertContext)} functionality to a simple String value.
   */
  public static abstract class StringConverter extends ResolvingConverter<String> {

    @Override
    public String fromString(final String s, final ConvertContext context) {
      return s;
    }

    @Override
    public String toString(final String s, final ConvertContext context) {
      return s;
    }
  }

  /**
   * Adds {@link #getVariants(ConvertContext)} functionality to an existing converter. 
   */
  public static abstract class WrappedResolvingConverter<T> extends ResolvingConverter<T> {

    private final Converter<T> myWrappedConverter;

    public WrappedResolvingConverter(Converter<T> converter) {

      myWrappedConverter = converter;
    }

    @Override
    public T fromString(final String s, final ConvertContext context) {
      return myWrappedConverter.fromString(s, context);
    }

    @Override
    public String toString(final T t, final ConvertContext context) {
      return myWrappedConverter.toString(t, context);
    }
  }

  /**
   * @deprecated unnecessary
   */
  @Deprecated
  public static final ResolvingConverter EMPTY_CONVERTER = new ResolvingConverter() {
    @Override
    @NotNull
    public Collection getVariants(final ConvertContext context) {
      return Collections.emptyList();
    }

    @Override
    public Object fromString(final String s, final ConvertContext context) {
      return s;
    }

    @Override
    public String toString(final Object t, final ConvertContext context) {
      return String.valueOf(t);
    }
  };

  /**
   * @deprecated see {@link com.intellij.util.xml.converters.values.BooleanValueConverter}
   */
  @Deprecated
  public static final Converter<Boolean> BOOLEAN_CONVERTER = new ResolvingConverter<>() {
    @Override
    public Boolean fromString(final String s, final ConvertContext context) {
      if ("true".equalsIgnoreCase(s)) {
        return Boolean.TRUE;
      }
      if ("false".equalsIgnoreCase(s)) {
        return Boolean.FALSE;
      }
      return null;
    }

    @Override
    public String toString(final Boolean t, final ConvertContext context) {
      return t == null ? null : t.toString();
    }

    @Override
    @NotNull
    public Collection<? extends Boolean> getVariants(final ConvertContext context) {
      final DomElement element = context.getInvocationElement();
      if (element instanceof GenericDomValue) {
        final SubTag annotation = element.getAnnotation(SubTag.class);
        if (annotation != null && annotation.indicator()) return Collections.emptyList();
      }

      return Arrays.asList(Boolean.FALSE, Boolean.TRUE);
    }
  };
}
