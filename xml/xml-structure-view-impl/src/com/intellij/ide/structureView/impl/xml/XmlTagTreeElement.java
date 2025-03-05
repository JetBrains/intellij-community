// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public class XmlTagTreeElement extends AbstractXmlTagTreeElement<XmlTag>{
  private static final @NonNls String ID_ATTR_NAME = "id";
  private static final @NonNls String NAME_ATTR_NAME = "name";

  public XmlTagTreeElement(XmlTag tag) {
    super(tag);
  }

 @Override
 public @NotNull @Unmodifiable Collection<StructureViewTreeElement> getChildrenBase() {
    return getStructureViewTreeElements(getElement().getSubTags());
  }

  @Override
  public String getPresentableText() {
    final XmlTag element = getElement();
    if (element == null) {
      return StructureViewBundle.message("node.structureview.invalid");
    }
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null) {
      id = element.getAttributeValue(NAME_ATTR_NAME);
    }
    id = toCanonicalForm(id);
    return id != null ? id + ':' + element.getLocalName() : element.getName();
  }

  @Override
  public String getLocationString() {
    final StringBuilder buffer = new StringBuilder();
    final XmlTag element = getElement();
    assert element != null;
    String id = element.getAttributeValue(ID_ATTR_NAME);
    String usedAttrName = null;
    if (id == null) {
      id = element.getAttributeValue(NAME_ATTR_NAME);
      if (id != null) {
        usedAttrName = NAME_ATTR_NAME;
      }
    }
    else {
      usedAttrName = ID_ATTR_NAME;
    }

    id = toCanonicalForm(id);

    for (XmlAttribute attribute : element.getAttributes()) {
      if (!buffer.isEmpty()) {
        buffer.append(' ');
      }

      final String name = attribute.getName();
      if (usedAttrName != null && id != null && usedAttrName.equals(name)) {
        continue; // we output this name in name
      }

      buffer.append(name);
      buffer.append('=').append('"').append(attribute.getValue()).append('"');
    }
    return buffer.toString();
  }

  public static @Nullable String toCanonicalForm(@Nullable String id) {
    if (id != null) {
      id = id.trim();
      if (id.isEmpty()) {
        return null;
      }
    }
    return id;
  }
}