// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.html.dtd;

import com.intellij.lang.html.HtmlCompatibleFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.html.HtmlEnumeratedReferenceSet;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Maxim.Mossienko
 */
public class HtmlAttributeDescriptorImpl extends BasicXmlAttributeDescriptor {
  private final XmlAttributeDescriptor delegate;
  private final boolean myCaseSensitive;

  public HtmlAttributeDescriptorImpl(XmlAttributeDescriptor _delegate, boolean caseSensitive) {
    delegate = _delegate;
    myCaseSensitive = caseSensitive;
  }

  @Override
  public boolean isRequired() {
    return delegate.isRequired();
  }

  @Override
  public boolean isFixed() {
    return delegate.isFixed();
  }

  @Override
  public boolean hasIdType() {
    return delegate.hasIdType();
  }

  @Override
  public boolean hasIdRefType() {
    return delegate.hasIdRefType();
  }

  @Override
  public String getDefaultValue() {
    return delegate.getDefaultValue();
  }

  //todo: refactor to hierarchy of value descriptor?
  @Override
  public boolean isEnumerated() {
    return delegate.isEnumerated();
  }

  @Override
  public String[] getEnumeratedValues() {
    return delegate.getEnumeratedValues();
  }

  @Override
  public String validateValue(XmlElement context, String value) {
    if (!myCaseSensitive) value = StringUtil.toLowerCase(value);
    return delegate.validateValue(context, value);
  }

  @Override
  public PsiElement getDeclaration() {
    return delegate.getDeclaration();
  }

  @Override
  public @NotNull Collection<PsiElement> getDeclarations() {
    return delegate.getDeclarations();
  }

  @Override
  public String getName(PsiElement context) {
    return delegate.getName(context);
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public void init(PsiElement element) {
    delegate.init(element);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  @Override
  public PsiElement getValueDeclaration(XmlElement attributeValue, String value) {
    String searchValue = null;
    if (!myCaseSensitive) {
      String[] enumeratedValues = isEnumerated() ? getEnumeratedValues() : null;
      if (enumeratedValues != null) {
        searchValue = ContainerUtil.find(getEnumeratedValues(), v -> v.equalsIgnoreCase(value));
      }
      if (searchValue == null) {
        searchValue = StringUtil.toLowerCase(value);
      }
    } else {
      searchValue = value;
    }
    //noinspection unchecked
    return delegate instanceof XmlEnumerationDescriptor ?
           ((XmlEnumerationDescriptor<XmlElement>)delegate).getValueDeclaration(attributeValue, searchValue) :
           super.getValueDeclaration(attributeValue, value);
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  @Override
  public PsiReference[] getValueReferences(XmlElement element, @NotNull String text) {
    if (element != null && element.getContainingFile() instanceof HtmlCompatibleFile)
      return new HtmlEnumeratedReferenceSet(element, this).getPsiReferences();
    else
      //noinspection unchecked
      return super.getValueReferences(element, text);
  }
}