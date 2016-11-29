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

package com.intellij.util.xml.converters;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.DelimitedListProcessor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class DelimitedListConverter<T> extends ResolvingConverter<List<T>> implements CustomReferenceConverter<List<T>> {

  protected final static Object[] EMPTY_ARRAY = ArrayUtil.EMPTY_OBJECT_ARRAY;

  private final String myDelimiters;

  public DelimitedListConverter(@NonNls @NotNull String delimiters) {

    myDelimiters = delimiters;
  }

  @Nullable
  protected abstract T convertString(final @Nullable String string, final ConvertContext context);

  @Nullable
  protected abstract String toString(@Nullable final T t);


  protected abstract Object[] getReferenceVariants(final ConvertContext context, GenericDomValue<List<T>> genericDomValue);

  @Nullable
  protected abstract PsiElement resolveReference(@Nullable final T t, final ConvertContext context);

  protected abstract String getUnresolvedMessage(String value);

  @Override
  @NotNull
  public Collection<? extends List<T>> getVariants(final ConvertContext context) {
    return Collections.emptyList();
  }

  public static <T> void filterVariants(List<T> variants, GenericDomValue<List<T>> genericDomValue) {
    final List<T> list = genericDomValue.getValue();
    if (list != null) {
      for (Iterator<T> i = variants.iterator(); i.hasNext(); ) {
        final T variant = i.next();
        for (T existing : list) {
          if (existing.equals(variant)) {
            i.remove();
            break;
          }
        }
      }
    }
  }

  protected char getDefaultDelimiter() {
    return myDelimiters.charAt(0);
  }

  @Override
  public List<T> fromString(@Nullable final String str, final ConvertContext context) {
    if (str == null) {
      return null;
    }
    List<T> values = new ArrayList<>();

    for (String s : StringUtil.tokenize(str, myDelimiters)) {
      final T t = convertString(s.trim(), context);
      if (t != null) {
        values.add(t);
      }
    }
    return values;
  }

  @Override
  public String toString(final List<T> ts, final ConvertContext context) {
    final StringBuilder buffer = new StringBuilder();
    final char delimiter = getDefaultDelimiter();
    for (T t : ts) {
      final String s = toString(t);
      if (s != null) {
        if (buffer.length() != 0) {
          buffer.append(delimiter);
        }
        buffer.append(s);
      }
    }
    return buffer.toString();
  }

  @Override
  @NotNull
  public PsiReference[] createReferences(final GenericDomValue<List<T>> genericDomValue,
                                         final PsiElement element,
                                         final ConvertContext context) {

    final String text = genericDomValue.getRawText();
    if (text == null) {
      return PsiReference.EMPTY_ARRAY;
    }

    final ArrayList<PsiReference> references = new ArrayList<>();
    new DelimitedListProcessor(myDelimiters) {
      @Override
      protected void processToken(final int start, final int end, final boolean delimitersOnly) {
        references.add(createPsiReference(element, start + 1, end + 1, context, genericDomValue, delimitersOnly));
      }
    }.processText(text);
    return references.toArray(new PsiReference[references.size()]);
  }

  @NotNull
  protected PsiReference createPsiReference(final PsiElement element,
                                            int start,
                                            int end,
                                            final ConvertContext context,
                                            final GenericDomValue<List<T>> genericDomValue,
                                            final boolean delimitersOnly) {

    return new MyPsiReference(element, getTextRange(genericDomValue, start, end), context, genericDomValue, delimitersOnly);
  }

  protected TextRange getTextRange(GenericDomValue value, int start, int end) {
    if (value instanceof GenericAttributeValue) {
      return new TextRange(start, end);
    }
    TextRange tagRange = XmlTagUtil.getTrimmedValueRange(value.getXmlTag());
    return new TextRange(tagRange.getStartOffset() + start - 1, tagRange.getStartOffset() + end - 1);
  }

  @Override
  public String toString() {
    return super.toString() + " delimiters: " + myDelimiters;
  }

  protected class MyPsiReference extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    protected final ConvertContext myContext;
    protected final GenericDomValue<List<T>> myGenericDomValue;
    private final boolean myDelimitersOnly;

    public MyPsiReference(final PsiElement element,
                          final TextRange range,
                          final ConvertContext context,
                          final GenericDomValue<List<T>> genericDomValue,
                          final boolean delimitersOnly) {
      this(element, range, context, genericDomValue, true, delimitersOnly);
    }

    public MyPsiReference(final PsiElement element,
                          final TextRange range,
                          final ConvertContext context,
                          final GenericDomValue<List<T>> genericDomValue,
                          boolean soft,
                          final boolean delimitersOnly) {
      super(element, range, soft);
      myContext = context;
      myGenericDomValue = genericDomValue;
      myDelimitersOnly = delimitersOnly;
    }

    @Override
    @Nullable
    public PsiElement resolve() {
      if (myDelimitersOnly) {
        return getElement();
      }
      final String value = getValue();
      return resolveReference(convertString(value, myContext), myContext);
    }

    @Override
    @NotNull
    public Object[] getVariants() {
      return getReferenceVariants(myContext, myGenericDomValue);
    }

    @Override
    public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
      final Ref<IncorrectOperationException> ref = new Ref<>();
      PsiElement element = referenceHandleElementRename(this, newElementName, getSuperElementRenameFunction(ref));
      if (!ref.isNull()) {
        throw ref.get();
      }

      return element;
    }

    @Override
    public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
      final Ref<IncorrectOperationException> ref = new Ref<>();
      PsiElement bindElement =
        referenceBindToElement(this, element, getSuperBindToElementFunction(ref), getSuperElementRenameFunction(ref));
      if (!ref.isNull()) {
        throw ref.get();
      }

      return bindElement;
    }

    @Override
    public String toString() {
      return super.toString() + " converter: " + DelimitedListConverter.this;
    }

    private Function<PsiElement, PsiElement> getSuperBindToElementFunction(final Ref<IncorrectOperationException> ref) {
      return s -> {
        try {
          return super.bindToElement(s);
        }
        catch (IncorrectOperationException e) {
          ref.set(e);
        }
        return null;
      };
    }

    private Function<String, PsiElement> getSuperElementRenameFunction(final Ref<IncorrectOperationException> ref) {
      return s -> {
        try {
          return super.handleElementRename(s);
        }
        catch (IncorrectOperationException e) {
          ref.set(e);
        }
        return null;
      };
    }


    @Override
    @NotNull
    public String getUnresolvedMessagePattern() {
      return getUnresolvedMessage(getValue());
    }
  }

  protected PsiElement referenceBindToElement(final PsiReference psiReference, final PsiElement element,
                                              final Function<PsiElement, PsiElement> superBindToElementFunction,
                                              final Function<String, PsiElement> superElementRenameFunction)
    throws IncorrectOperationException {
    return superBindToElementFunction.fun(element);
  }

  protected PsiElement referenceHandleElementRename(final PsiReference psiReference,
                                                    final String newName,
                                                    final Function<String, PsiElement> superHandleElementRename)
    throws IncorrectOperationException {

    return superHandleElementRename.fun(newName);
  }
}
