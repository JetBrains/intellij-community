// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xml;

import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public interface XmlAttributeDescriptor extends PsiMetaData {
  XmlAttributeDescriptor[] EMPTY = new XmlAttributeDescriptor[0];
  ArrayFactory<XmlAttributeDescriptor> ARRAY_FACTORY = count -> count == 0 ? EMPTY : new XmlAttributeDescriptor[count];

  boolean isRequired();
  boolean isFixed();
  boolean hasIdType();
  boolean hasIdRefType();

  @Nullable
  String getDefaultValue();

  //todo: refactor to hierarchy of value descriptor?
  boolean isEnumerated();
  String @Nullable [] getEnumeratedValues();

  @Nullable
  @DetailedDescription
  String validateValue(XmlElement context, String value);

  default @NotNull Collection<PsiElement> getDeclarations() {
    PsiElement declaration = getDeclaration();
    return declaration != null ? Collections.singleton(declaration)
                               : Collections.emptyList();
  }
}
