/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.javaee.J2EEBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ModelMerger;
import com.intellij.util.xml.GenericAttributeValue;

import java.util.List;

/**
 * author: lesya
 */
public class GenericDomValueReference<T> extends GenericReference {
  private final GenericDomValue<T> myXmlValue;
  private final XmlElement myElement;
  private final TextRange myTextRange;

  public GenericDomValueReference(final PsiReferenceProvider provider, GenericDomValue<T> xmlValue, TextRange textRange) {
    super(provider);
    myXmlValue = xmlValue;
    myTextRange = textRange;
    myElement = xmlValue instanceof GenericAttributeValue ? ((GenericAttributeValue) xmlValue).getXmlAttributeValue() : xmlValue.getXmlTag();
  }

  public XmlElement getContext() {
    return myElement;
  }

  public PsiReference getContextReference() {
    return null;
  }

  public ReferenceType getType() {
    return new ReferenceType(ReferenceType.UNKNOWN);
  }

  protected PsiElement resolveInner(T o) {
    if (o instanceof PsiElement) {
      return (PsiElement)o;
    }
    if (o instanceof DomElement) {
      return ((DomElement)o).getXmlTag();
    }
    if (o instanceof ModelMerger.MergedObject) {
      final List<T> list = ((ModelMerger.MergedObject<T>)o).getImplementations();
      for (final T o1 : list) {
        final PsiElement psiElement = resolveInner(o1);
        if (psiElement != null) {
          return psiElement;
        }
      }
    }
    return null;
  }

  public PsiElement resolveInner() {
    return resolveInner(myXmlValue.getValue());
  }

  public ReferenceType getSoftenType() {
    return getType();
  }

  public boolean needToCheckAccessibility() {
    return false;
  }

  public XmlElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  public String getCanonicalText() {
    String value = myXmlValue.getStringValue();
    return value != null ? value : J2EEBundle.message("unknown.j2ee.reference.canonical.text");
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    myXmlValue.setStringValue(newElementName);
    return myXmlValue.getXmlTag();
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiClass) {
      myXmlValue.setStringValue(((PsiClass)element).getName());
      return myXmlValue.getXmlTag();
    }
    if (element instanceof XmlTag) {
      myXmlValue.setStringValue(((XmlTag)element).getName());
      return myXmlValue.getXmlTag();
    }
    return null;
  }

  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
