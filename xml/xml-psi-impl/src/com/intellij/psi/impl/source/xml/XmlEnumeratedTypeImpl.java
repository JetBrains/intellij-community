// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.filters.position.XmlTokenTypeFilter;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEnumeratedType;
import com.intellij.psi.xml.XmlTokenType;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.xml.XmlElementType.XML_ENUMERATED_TYPE;

public class XmlEnumeratedTypeImpl extends XmlElementImpl implements XmlEnumeratedType {
  public XmlEnumeratedTypeImpl() {
    super(XML_ENUMERATED_TYPE);
  }

  @Override
  public XmlElement[] getEnumeratedValues() {
    final List<XmlElement> result = new ArrayList<>();
    processElements(new FilterElementProcessor(new XmlTokenTypeFilter(XmlTokenType.XML_NAME), result), this);
    return result.toArray(XmlElement.EMPTY_ARRAY);
  }
}
