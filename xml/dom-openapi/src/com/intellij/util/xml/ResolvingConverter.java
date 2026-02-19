// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * If converter extends this class, the corresponding XML {@link com.intellij.psi.PsiReference}
 * will take completion variants from {@link #getVariants(ConvertContext)} method.
 */
public abstract class ResolvingConverter<T> extends Converter<T> implements ResolvingHint {

  @Override
  public @InspectionMessage String getErrorMessage(@Nullable String s, final @NotNull ConvertContext context) {
    return AnalysisBundle.message("error.cannot.resolve.default.message", s);
  }

  /**
   * @param context context
   * @return reference completion variants
   */
  public abstract @Unmodifiable @NotNull Collection<? extends T> getVariants(@NotNull ConvertContext context);

  /**
   * @return additional reference variants. They won't resolve to anywhere, but won't be highlighted as errors.
   * They will also appear in the completion dropdown.
   */
  public @NotNull @Unmodifiable Set<String> getAdditionalVariants(final @NotNull ConvertContext context) {
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
  public @Nullable PsiElement getPsiElement(@Nullable T resolvedValue) {
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
                               @NotNull ConvertContext context) {
    return resolveResult != null && element.getManager().areElementsEquivalent(element, getPsiElement(resolveResult));
  }

  @Override
  public boolean canResolveTo(@NotNull Class<? extends PsiElement> elementClass) {
    return true;
  }

  /**
   * Delegate from {@link com.intellij.psi.PsiReference#resolve()}
   * @param o {@link #fromString(String, ConvertContext)} result
   * @param context context
   * @return PSI element to resolve to. By default calls {@link #getPsiElement(Object)} method
   */
  public @Nullable PsiElement resolve(final T o, @NotNull ConvertContext context) {
    final PsiElement psiElement = getPsiElement(o);
    return psiElement == null && o != null ? DomUtil.getValueElement((GenericDomValue)context.getInvocationElement()) : psiElement;
  }

  /**
   * @param context context
   * @return LocalQuickFix'es to correct non-resolved value (e.g. 'create from usage')
   */
  public LocalQuickFix[] getQuickFixes(@NotNull ConvertContext context) {
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
  public @Nullable LookupElement createLookupElement(T t) {
    return null;
  }

  /**
   * Adds {@link #getVariants(ConvertContext)} functionality to a simple String value.
   */
  public abstract static class StringConverter extends ResolvingConverter<String> {

    @Override
    public String fromString(final String s, final @NotNull ConvertContext context) {
      return s;
    }

    @Override
    public String toString(final String s, final @NotNull ConvertContext context) {
      return s;
    }
  }

  /**
   * Adds {@link #getVariants(ConvertContext)} functionality to an existing converter. 
   */
  public abstract static class WrappedResolvingConverter<T> extends ResolvingConverter<T> {

    private final Converter<T> myWrappedConverter;

    public WrappedResolvingConverter(Converter<T> converter) {

      myWrappedConverter = converter;
    }

    @Override
    public T fromString(final String s, final @NotNull ConvertContext context) {
      return myWrappedConverter.fromString(s, context);
    }

    @Override
    public String toString(final T t, final @NotNull ConvertContext context) {
      return myWrappedConverter.toString(t, context);
    }
  }

  /**
   * @deprecated unnecessary
   */
  @Deprecated
  public static final ResolvingConverter EMPTY_CONVERTER = new ResolvingConverter() {
    @Override
    public @NotNull Collection getVariants(final @NotNull ConvertContext context) {
      return Collections.emptyList();
    }

    @Override
    public Object fromString(final String s, final @NotNull ConvertContext context) {
      return s;
    }

    @Override
    public String toString(final Object t, final @NotNull ConvertContext context) {
      return String.valueOf(t);
    }
  };

  /**
   * @deprecated see {@link com.intellij.util.xml.converters.values.BooleanValueConverter}
   */
  @Deprecated
  public static final Converter<Boolean> BOOLEAN_CONVERTER = new ResolvingConverter<>() {
    @Override
    public Boolean fromString(final String s, final @NotNull ConvertContext context) {
      if ("true".equalsIgnoreCase(s)) {
        return Boolean.TRUE;
      }
      if ("false".equalsIgnoreCase(s)) {
        return Boolean.FALSE;
      }
      return null;
    }

    @Override
    public String toString(final Boolean t, final @NotNull ConvertContext context) {
      return t == null ? null : t.toString();
    }

    @Override
    public @NotNull Collection<? extends Boolean> getVariants(final @NotNull ConvertContext context) {
      final DomElement element = context.getInvocationElement();
      if (element instanceof GenericDomValue) {
        final SubTag annotation = element.getAnnotation(SubTag.class);
        if (annotation != null && annotation.indicator()) return Collections.emptyList();
      }

      return Arrays.asList(Boolean.FALSE, Boolean.TRUE);
    }
  };
}
