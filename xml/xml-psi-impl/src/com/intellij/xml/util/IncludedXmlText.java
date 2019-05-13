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
package com.intellij.xml.util;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
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
  @Nullable
  public XmlText split(int displayIndex) {
    throw new UnsupportedOperationException("Can't modify included elements");
  }
}
