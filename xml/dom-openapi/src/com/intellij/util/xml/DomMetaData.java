// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DomMetaData<T extends DomElement> implements PsiWritableMetaData, PsiPresentableMetaData, PsiMetaData {
  private T myElement;
  private @Nullable GenericDomValue myNameElement;

  @Override
  public final PsiElement getDeclaration() {
    return myElement.getXmlTag();
  }

  public T getElement() {
    return myElement;
  }

  @Override
  public @NonNls String getName(PsiElement context) {
    return getName();
  }

  @Override
  public final @NonNls String getName() {
    final String s = ElementPresentationManager.getElementName(myElement);
    if (s != null) return s;

    final GenericDomValue value = getNameElement(myElement);
    return value == null ? null : value.getStringValue();
  }

  @Override
  public void init(PsiElement element) {
    myElement = (T) DomManager.getDomManager(element.getProject()).getDomElement((XmlTag)element);
    assert myElement != null : element;
    myNameElement = getNameElement(myElement);
  }

  public void setElement(final T element) {
    myElement = element;
  }

  protected @Nullable GenericDomValue getNameElement(final T t) {
    return myElement.getGenericInfo().getNameDomElement(t);
  }
  
  @ApiStatus.Internal
  public static <T extends DomElement> @Nullable GenericDomValue<?> getNameElement(@NotNull DomMetaData<T> domMetaData, T element) {
    return domMetaData.getNameElement(element);
  }

  @Override
  public Object @NotNull [] getDependencies() {
    final PsiElement declaration = getDeclaration();
    if (myElement != null && myElement.isValid()) {
      return new Object[]{DomUtil.getRoot(myElement), declaration};
    }
    return new Object[]{declaration};
  }

  @Override
  public void setName(String name) throws IncorrectOperationException {
    if (myNameElement != null) {
      myNameElement.setStringValue(name);
    }
  }

  @Override
  public String getTypeName() {
    return ElementPresentationManager.getTypeNameForObject(myElement);
  }

  @Override
  public Icon getIcon() {
    return ElementPresentationManager.getIcon(myElement);
  }
}
