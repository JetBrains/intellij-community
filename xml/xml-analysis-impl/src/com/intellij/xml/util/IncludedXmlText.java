// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IncludedXmlText extends IncludedXmlElement<XmlText> implements XmlText {
  public IncludedXmlText(@NotNull XmlText original, @Nullable XmlTag parent) {
    super(original, parent);
  }

  @Override
  public XmlTag getParentTag() {
    return (XmlTag)getParent();
  }

  @Override
  public XmlTagChild getNextSiblingInTag() {
    return null;
  }

  @Override
  public XmlTagChild getPrevSiblingInTag() {
    return null;
  }

  @Override
  public String getText() {
    return getOriginal().getText();
  }

  @Override
  public String getValue() {
    return getOriginal().getValue();
  }

  @Override
  public void setValue(String s) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Can't modify included elements");
  }

  @Override
  public XmlElement insertAtOffset(XmlElement element, int displayOffset) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Can't modify included elements");
  }

  @Override
  public void insertText(String text, int displayOffset) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Can't modify included elements");
  }

  @Override
  public void removeText(int displayStart, int displayEnd) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Can't modify included elements");
  }

  @Override
  public int physicalToDisplay(int offset) {
    return getOriginal().physicalToDisplay(offset);
  }

  @Override
  public int displayToPhysical(int offset) {
    return getOriginal().displayToPhysical(offset);
  }

  @Override
  public @Nullable XmlText split(int displayIndex) {
    throw new UnsupportedOperationException("Can't modify included elements");
  }
}
