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

/*
 * Created by IntelliJ IDEA.
 * User: Sergey.Vasiliev
 * Date: Nov 13, 2006
 * Time: 4:37:22 PM
 */
package com.intellij.util.xml.converters;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.xml.*;
import com.intellij.xml.util.XmlTagTextUtil;
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

  @Nullable
  protected abstract T convertString(final @Nullable String string, final ConvertContext context);

  @Nullable
  protected abstract String convertValue(@Nullable final T t, final ConvertContext context);

  protected abstract Object[] getReferenceVariants(final ConvertContext context, GenericDomValue<T> genericDomValue,
                                                   final TextRange rangeInElement);

  @NotNull
  protected abstract ResolveResult[] multiResolveReference(@Nullable final T t, final ConvertContext context);

  protected abstract String getUnresolvedMessage(String value);

  @NotNull
  public Collection<? extends T> getVariants(final ConvertContext context) {
    return Collections.emptyList();
  }

  public T fromString(final String str, final ConvertContext context) {
    return convertString(unquote(str, getQuoteSigns()), context);
  }

  public String toString(final T ts, final ConvertContext context) {
    final char delimiter = getQuoteSign(ts, context);
    final String s = convertValue(ts, context);
    return delimiter > 0? delimiter + s+ delimiter : s;
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue<T> genericDomValue,
                                         final PsiElement element,
                                         final ConvertContext context) {
    final String originalValue = genericDomValue.getStringValue();
    if (originalValue == null) return PsiReference.EMPTY_ARRAY;
    final String unquotedValue = unquote(originalValue, getQuoteSigns());
    int startOffset = originalValue == unquotedValue? 0 : XmlTagTextUtil.escapeString(originalValue.substring(0, 1), false).length();
    int endOffset = originalValue == unquotedValue || quotationIsNotClosed(originalValue)? 0 : startOffset;
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    assert manipulator != null : "manipulator not found";
    final TextRange range = manipulator.getRangeInElement(element);
    return new PsiReference[]{createPsiReference(element, range.getStartOffset()+startOffset, range.getEndOffset() - endOffset, true, context, genericDomValue, startOffset != endOffset)};
  }

  @Nullable
  public static String unquote(final String str) {
    return unquote(str, QUOTE_SIGNS);
  }

  @Nullable
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

  @NotNull
  protected PsiReference createPsiReference(final PsiElement element,
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

    @NotNull
    public ResolveResult[] multiResolve(final boolean incompleteCode) {
      if (myBadQuotation) return ResolveResult.EMPTY_ARRAY;
      final String value = getValue();
      return multiResolveReference(convertString(value, myContext), myContext);
    }

    @NotNull
    public Object[] getVariants() {
      return getReferenceVariants(myContext, myGenericDomValue, getRangeInElement());
    }

    public String getUnresolvedMessagePattern() {
      return myBadQuotation? DomBundle.message("message.invalid.value.quotation") : getUnresolvedMessage(getValue());
    }
  }
}
