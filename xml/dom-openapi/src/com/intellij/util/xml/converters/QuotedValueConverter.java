// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.converters;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class QuotedValueConverter<T> extends ResolvingConverter<T> implements CustomReferenceConverter<T> {

  public static final char[] QUOTE_SIGNS = new char[] {'\'', '\"', '`'};

  protected char[] getQuoteSigns() {
    return QUOTE_SIGNS;
  }

  protected char getQuoteSign(final T t, final ConvertContext context) {
    return 0;
  }

  protected abstract @Nullable T convertString(final @Nullable String string, final ConvertContext context);

  protected abstract @Nullable String convertValue(final @Nullable T t, final ConvertContext context);

  protected abstract Object[] getReferenceVariants(final ConvertContext context, GenericDomValue<T> genericDomValue,
                                                   final TextRange rangeInElement);

  protected abstract ResolveResult @NotNull [] multiResolveReference(final @Nullable T t, final ConvertContext context);

  protected abstract @InspectionMessage String getUnresolvedMessage(String value);

  @Override
  public @NotNull Collection<? extends T> getVariants(final @NotNull ConvertContext context) {
    return Collections.emptyList();
  }

  @Override
  public T fromString(final String str, final @NotNull ConvertContext context) {
    return convertString(unquote(str, getQuoteSigns()), context);
  }

  @Override
  public String toString(final T ts, final @NotNull ConvertContext context) {
    final char delimiter = getQuoteSign(ts, context);
    final String s = convertValue(ts, context);
    return delimiter > 0? delimiter + s+ delimiter : s;
  }

  @Override
  public PsiReference @NotNull [] createReferences(final GenericDomValue<T> genericDomValue,
                                                   final PsiElement element,
                                                   final ConvertContext context) {
    final String originalValue = genericDomValue.getStringValue();
    if (originalValue == null) return PsiReference.EMPTY_ARRAY;
    TextRange range = ElementManipulators.getValueTextRange(element);
    String unquotedValue = unquote(originalValue, getQuoteSigns());
    int valueOffset = range.substring(element.getText()).indexOf(unquotedValue);
    if (valueOffset < 0) return PsiReference.EMPTY_ARRAY;
    int start = range.getStartOffset() + valueOffset;
    int end = start + unquotedValue.length();
    boolean unclosedQuotation = valueOffset > 0 && end == range.getEndOffset();
    return new PsiReference[]{createPsiReference(element, start, end, true, context, genericDomValue, unclosedQuotation)};
  }

  public static @Nullable String unquote(final String str) {
    return unquote(str, QUOTE_SIGNS);
  }

  @Contract("null, _ -> null")
  public static String unquote(final String str, final char[] quoteSigns) {
    if (str != null && str.length() > 2) {
      final char c = str.charAt(0);
      for (char quote : quoteSigns) {
        if (quote == c) {
          return str.substring(1, c == str.charAt(str.length() - 1)? str.length() - 1 : str.length());
        }
      }
    }
    return str;
  }

  public static boolean quotationIsNotClosed(final String str) {
    return StringUtil.isNotEmpty(str) && str.charAt(0) != str.charAt(str.length()-1);
  }

  protected @NotNull PsiReference createPsiReference(final PsiElement element,
                                                     int start, int end,
                                                     final boolean isSoft,
                                                     final ConvertContext context,
                                                     final GenericDomValue<T> genericDomValue,
                                                     final boolean badQuotation) {

    return new MyPsiReference(element, new TextRange(start, end), isSoft, context, genericDomValue, badQuotation);
  }

  protected class MyPsiReference extends PsiPolyVariantReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    protected final ConvertContext myContext;
    protected final GenericDomValue<T> myGenericDomValue;
    private final boolean myBadQuotation;

    public MyPsiReference(final PsiElement element, final TextRange range, final boolean isSoft, final ConvertContext context, final GenericDomValue<T> genericDomValue,
                          final boolean badQuotation) {
      super(element, range, isSoft);
      myContext = context;
      myGenericDomValue = genericDomValue;
      myBadQuotation = badQuotation;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {
      if (myBadQuotation) return ResolveResult.EMPTY_ARRAY;
      final String value = getValue();
      return multiResolveReference(convertString(value, myContext), myContext);
    }

    @Override
    public Object @NotNull [] getVariants() {
      return getReferenceVariants(myContext, myGenericDomValue, getRangeInElement());
    }

    @SuppressWarnings("UnresolvedPropertyKey")
    @Override
    public @NotNull String getUnresolvedMessagePattern() {
      return myBadQuotation ? XmlDomBundle.message("dom.inspections.invalid.value.quotation") : getUnresolvedMessage(getValue());
    }
  }
}
