/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.xml.impl.schema;

import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementsGroup;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlElementsGroupBase implements XmlElementsGroup {

  protected final XmlTag myTag;
  private final XmlElementsGroup myParent;

  public XmlElementsGroupBase(XmlTag tag, XmlElementsGroup parent) {
    myTag = tag;
    myParent = parent;
  }

  @Override
  public int getMinOccurs() {
    String value = myTag.getAttributeValue("minOccurs");
    try {
      return value == null ? 1 : Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return 1;
    }
  }

  @Override
  public int getMaxOccurs() {
    String value = myTag.getAttributeValue("maxOccurs");
    if (value == null) return 1;
    if ("unbounded".equals(value)) return Integer.MAX_VALUE;
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return 1;
    }
  }

  @Override
  public XmlElementsGroup getParentGroup() {
    return myParent;
  }
}
