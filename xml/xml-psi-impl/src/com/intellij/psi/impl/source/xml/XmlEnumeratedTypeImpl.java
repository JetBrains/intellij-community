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
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.filters.position.XmlTokenTypeFilter;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlEnumeratedType;
import com.intellij.psi.xml.XmlTokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class XmlEnumeratedTypeImpl extends XmlElementImpl implements XmlEnumeratedType, XmlElementType {
  public XmlEnumeratedTypeImpl() {
    super(XML_ENUMERATED_TYPE);
  }

  @Override
  public XmlElement[] getEnumeratedValues() {
    final List<XmlElement> result = new ArrayList<>();
    processElements(new FilterElementProcessor(new XmlTokenTypeFilter(XmlTokenType.XML_NAME), result), this);
    return result.toArray(new XmlElement[result.size()]);
  }
}
