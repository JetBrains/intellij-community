// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.converters;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.DelimitedListProcessor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class DelimitedListConverter<T> extends ResolvingConverter<List<T>> implements CustomReferenceConverter<List<T>> {

  protected static final Object[] EMPTY_ARRAY = ArrayUtilRt.EMPTY_OBJECT_ARRAY;

  private final String myDelimiters;

  public DelimitedListConverter(@NonNls @NotNull String delimiters) {

    myDelimiters = delimiters;
  }

  protected abstract @Nullable T convertString(final @Nullable String string, @NotNull ConvertContext context);

  protected abstract @Nullable String toString(final @Nullable T t);


  protected abstract Object[] getReferenceVariants(@NotNull ConvertContext context, GenericDomValue<? extends List<T>> genericDomValue);

  protected abstract @Nullable PsiElement resolveReference(final @Nullable T t, @NotNull ConvertContext context);

  protected abstract @InspectionMessage String getUnresolvedMessage(String value);

  @Override
  public @NotNull Collection<? extends List<T>> getVariants(final @NotNull ConvertContext context) {
    return Collections.emptyList();
  }

  public static <T> void filterVariants(List<T> variants, GenericDomValue<? extends List<T>> genericDomValue) {
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
  public List<T> fromString(final @Nullable String str, final @NotNull ConvertContext context) {
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
  public String toString(final List<T> ts, final @NotNull ConvertContext context) {
    final StringBuilder buffer = new StringBuilder();
    final char delimiter = getDefaultDelimiter();
    for (T t : ts) {
      final String s = toString(t);
      if (s != null) {
        if (!buffer.isEmpty()) {
          buffer.append(delimiter);
        }
        buffer.append(s);
      }
    }
    return buffer.toString();
  }

  @Override
  public PsiReference @NotNull [] createReferences(final GenericDomValue<List<T>> genericDomValue,
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
    return references.toArray(PsiReference.EMPTY_ARRAY);
  }

  protected @NotNull PsiReference createPsiReference(final PsiElement element,
                                                     int start,
                                                     int end,
                                                     @NotNull ConvertContext context,
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
                          @NotNull ConvertContext context,
                          final GenericDomValue<List<T>> genericDomValue,
                          final boolean delimitersOnly) {
      this(element, range, context, genericDomValue, true, delimitersOnly);
    }

    public MyPsiReference(final PsiElement element,
                          final TextRange range,
                          @NotNull ConvertContext context,
                          final GenericDomValue<List<T>> genericDomValue,
                          boolean soft,
                          final boolean delimitersOnly) {
      super(element, range, soft);
      myContext = context;
      myGenericDomValue = genericDomValue;
      myDelimitersOnly = delimitersOnly;
    }

    @Override
    public @Nullable PsiElement resolve() {
      if (myDelimitersOnly) {
        return getElement();
      }
      final String value = getValue();
      return DelimitedListConverter.this.resolveReference(convertString(value, myContext), myContext);
    }

    @Override
    public Object @NotNull [] getVariants() {
      return getReferenceVariants(myContext, myGenericDomValue);
    }

    @Override
    public PsiElement handleElementRename(final @NotNull String newElementName) throws IncorrectOperationException {
      final Ref<IncorrectOperationException> ref = new Ref<>();
      PsiElement element = referenceHandleElementRename(this, newElementName, getSuperElementRenameFunction(ref));
      if (!ref.isNull()) {
        throw ref.get();
      }

      return element;
    }

    @Override
    public PsiElement bindToElement(final @NotNull PsiElement element) throws IncorrectOperationException {
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

    private Function<PsiElement, PsiElement> getSuperBindToElementFunction(final Ref<? super IncorrectOperationException> ref) {
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

    private Function<String, PsiElement> getSuperElementRenameFunction(final Ref<? super IncorrectOperationException> ref) {
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
    public @NotNull String getUnresolvedMessagePattern() {
      return getUnresolvedMessage(getValue());
    }
  }

  protected PsiElement referenceBindToElement(final PsiReference psiReference, final PsiElement element,
                                              final Function<? super PsiElement, ? extends PsiElement> superBindToElementFunction,
                                              final Function<? super String, ? extends PsiElement> superElementRenameFunction)
    throws IncorrectOperationException {
    return superBindToElementFunction.fun(element);
  }

  protected PsiElement referenceHandleElementRename(final PsiReference psiReference,
                                                    final String newName,
                                                    final Function<? super String, ? extends PsiElement> superHandleElementRename)
    throws IncorrectOperationException {

    return superHandleElementRename.fun(newName);
  }
}
