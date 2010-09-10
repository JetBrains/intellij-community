/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class XmlTagTreeElement extends AbstractXmlTagTreeElement<XmlTag>{
  @NonNls private static final String ID_ATTR_NAME = "id";
  @NonNls private static final String NAME_ATTR_NAME = "name";

  public XmlTagTreeElement(XmlTag tag) {
    super(tag);
  }

 @NotNull
 public Collection<StructureViewTreeElement> getChildrenBase() {
    return getStructureViewTreeElements(getElement().getSubTags());
  }

  public String getPresentableText() {
    final XmlTag element = getElement();
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null) id = element.getAttributeValue(NAME_ATTR_NAME);
    id = toCanonicalForm(id);

    if (id != null) return id + ":" + element.getLocalName();
    return element.getName();
  }

  public String getLocationString() {
    final StringBuffer buffer = new StringBuffer();
    final XmlTag element = getElement();
    final XmlAttribute[] attributes = element.getAttributes();

    String id = element.getAttributeValue(ID_ATTR_NAME);
    String usedAttrName = null;

    if (id == null) {
      id = element.getAttributeValue(NAME_ATTR_NAME);
      if (id != null) usedAttrName = NAME_ATTR_NAME;
    }
    else {
      usedAttrName = ID_ATTR_NAME;
    }

    id = toCanonicalForm(id);

    for (XmlAttribute attribute : attributes) {
      if (buffer.length() != 0) {
        buffer.append(" ");
      }

      final String name = attribute.getName();
      if (usedAttrName != null &&
          id != null &&
          usedAttrName.equals(name)
        ) {
        continue; // we output this name in name
      }

      buffer.append(name);
      buffer.append("=");
      buffer.append("\"");
      buffer.append(attribute.getValue());
      buffer.append("\"");
    }
    return buffer.toString();
  }

  @Nullable
  private static String toCanonicalForm(@Nullable String id) {
    if (id != null) {
      id = id.trim();
      if (id.length() == 0) id = null;
    }
    return id;
  }
}
