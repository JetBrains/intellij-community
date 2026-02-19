// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlAttribute extends XmlElement, PsiNamedElement, PsiExternalReferenceHost {
  XmlAttribute[] EMPTY_ARRAY = new XmlAttribute[0];

  @Override
  @NlsSafe
  @NotNull
  String getName();

  @NlsSafe
  @NotNull
  String getLocalName();

  XmlElement getNameElement();

  @NlsSafe
  @NotNull
  String getNamespace();

  @NlsSafe
  @NotNull
  String getNamespacePrefix();

  @Override
  XmlTag getParent();

  /**
   * @return text inside XML attribute with quotes stripped off
   */
  @Nullable @NlsSafe String getValue();

  /**
   * @return text inside XML attribute with quotes stripped off and XML char entities replaced with corresponding characters
   */
  @Nullable @NlsSafe String getDisplayValue();

  /**
   * @param offset in string returned by {@link #getValue()} (with quotes stripped)
   * @return offset in the string returned from {@link #getDisplayValue()} or -1 if the offset is out of valid range
   */
  int physicalToDisplay(int offset);

  /**
   * @param offset in the string returned from {@link #getDisplayValue()}
   * @return offset in string returned by {@link #getValue()} (with quotes stripped) or -1 if the offset is out of valid range
   */
  int displayToPhysical(int offset);

  /**
   * @return TextRange of the XML attribute value.
   * If quotes are present, it returns {@code new TextRange(1, getValue().getTextLength()-1)}, otherwise it is {@code new TextRange(0, getValue().getTextLength())}
   */
  @NotNull
  TextRange getValueTextRange();

  /**
   * @return true if the attribute is a namespace declaration (its name equals to {@code xmlns} or starts with {@code xmlns:})
   */
  boolean isNamespaceDeclaration();

  @Nullable
  XmlAttributeDescriptor getDescriptor();

  // In this case function is also used to get references from attribute value
  @Nullable
  XmlAttributeValue getValueElement();

  void setValue(@NotNull @NlsSafe String value) throws IncorrectOperationException;
}
