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

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolvingHint;
import com.intellij.psi.impl.PomTargetPsiElementImpl;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class GenericDomValueReference<T> extends PsiReferenceBase<XmlElement> implements EmptyResolveMessageProvider, ResolvingHint {
  private final GenericDomValue<T> myGenericValue;

  public GenericDomValueReference(GenericDomValue<T> domValue) {
    super(DomUtil.getValueElement(domValue));
    myGenericValue = domValue;
    assert DomUtil.hasXml(domValue);
    setRangeInElement(createTextRange());
  }

  protected final PsiManager getPsiManager() {
    return PsiManager.getInstance(myGenericValue.getManager().getProject());
  }

  protected TextRange createTextRange() {
    if (myGenericValue instanceof GenericAttributeValue) {
      final GenericAttributeValue genericAttributeValue = (GenericAttributeValue)myGenericValue;
      final XmlAttributeValue attributeValue = genericAttributeValue.getXmlAttributeValue();
      if (attributeValue == null) {
        return TextRange.from(0, genericAttributeValue.getXmlAttribute().getTextLength());
      }

      final int length = attributeValue.getTextLength();
      return length < 2 ? TextRange.from(0, length) : new TextRange(1, length - 1);
    }
    final XmlTag tag = myGenericValue.getXmlTag();
    assert tag != null;
    return XmlTagUtil.getTrimmedValueRange(tag);
  }

  protected final GenericDomValue<T> getGenericValue() {
    return myGenericValue;
  }

  @Override
  public boolean isSoft() {
    return true;
  }

  @Nullable
  protected PsiElement resolveInner(T o) {
    final Converter<T> converter = getConverter();
    if (converter instanceof ResolvingConverter) {
      return ((ResolvingConverter<T>)converter).resolve(o, getConvertContext());
    }

    if (o instanceof PsiElement) {
      return (PsiElement)o;
    }
    if (o instanceof DomElement) {
      DomTarget target = DomTarget.getTarget((DomElement)o);
      return target == null ? null : PomService.convertToPsi(target);
    }
    if (o instanceof MergedObject) {
      final List<T> list = ((MergedObject<T>)o).getImplementations();
      for (final T o1 : list) {
        final PsiElement psiElement = resolveInner(o1);
        if (psiElement != null) {
          return psiElement;
        }
      }
    }
    return o != null ? getElement() : null;
  }

  @Override
  public boolean isReferenceTo(final PsiElement element) {
    final Converter<T> converter = getConverter();
    if (converter instanceof ResolvingConverter) {
      T value = myGenericValue.getValue();
      if (value instanceof DomElement && element instanceof PomTargetPsiElementImpl) {
        PomTarget target = ((PomTargetPsiElementImpl)element).getTarget();
        if (target instanceof DomTarget) {
          if (value.equals(((DomTarget)target).getDomElement())) {
            return true;
          }
        }
      }
      return ((ResolvingConverter<T>)converter).isReferenceTo(element, getStringValue(), value, getConvertContext());
    }
    return super.isReferenceTo(element);
  }

  private String getStringValue() {
    return myGenericValue.getStringValue();
  }

  public Converter<T> getConverter() {
    return WrappingConverter.getDeepestConverter(myGenericValue.getConverter(), myGenericValue);
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    final T value = myGenericValue.getValue();
    return value == null ? null : resolveInner(value);
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return StringUtil.notNullize(getStringValue());
  }

  @Override
  @NotNull
  public String getUnresolvedMessagePattern() {
    final ConvertContext context = getConvertContext();
    return getConverter().getErrorMessage(getStringValue(), context);
  }

  public final ConvertContext getConvertContext() {
    return ConvertContextFactory.createConvertContext(DomManagerImpl.getDomInvocationHandler(myGenericValue));
  }

  @Override
  public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
    final Converter<T> converter = getConverter();
    if (converter instanceof ResolvingConverter) {
      ((ResolvingConverter)converter).handleElementRename(myGenericValue, getConvertContext(), newElementName);
      return myGenericValue.getXmlTag();
    }
    return super.handleElementRename(newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    final Converter<T> converter = getConverter();
    if (converter instanceof ResolvingConverter) {
      ((ResolvingConverter)converter).bindReference(myGenericValue, getConvertContext(), element);
      return myGenericValue.getXmlTag();
    }

    if (element instanceof XmlTag) {
      DomElement domElement = myGenericValue.getManager().getDomElement((XmlTag) element);
      if (domElement != null) {
        myGenericValue.setValue((T)domElement);
      } else {
        myGenericValue.setStringValue(((XmlTag)element).getName());
      }
      return myGenericValue.getXmlTag();
    }
    return null;
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    final Converter<T> converter = getConverter();
    if (converter instanceof EnumConverter || converter == ResolvingConverter.BOOLEAN_CONVERTER) {
      if (DomCompletionContributor.isSchemaEnumerated(getElement())) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    if (converter instanceof ResolvingConverter) {
      final ResolvingConverter<T> resolvingConverter = (ResolvingConverter<T>)converter;
      ArrayList<Object> result = new ArrayList<>();
      final ConvertContext convertContext = getConvertContext();
      for (T variant: resolvingConverter.getVariants(convertContext)) {
        LookupElement lookupElement = resolvingConverter.createLookupElement(variant);
        if (lookupElement != null) {
          result.add(lookupElement);
          continue;
        }
        String name = converter.toString(variant, convertContext);
        if (name != null) {
          result.add(ElementPresentationManager.getInstance().createVariant(variant, name, ((ResolvingConverter)converter).getPsiElement(variant)));
        }
      }
      for (final String string : resolvingConverter.getAdditionalVariants(convertContext)) {
        result.add(LookupValueFactory.createLookupValue(string, null));
      }
      return result.toArray();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean canResolveTo(Class<? extends PsiElement> elementClass) {
    Converter<T> converter = getConverter();
    return !(converter instanceof ResolvingConverter) || ((ResolvingConverter)converter).canResolveTo(elementClass);
  }
}
