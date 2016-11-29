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
package com.intellij.util.xml.stubs.builder;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.io.StringRef;
import com.intellij.util.xml.Stubbed;
import com.intellij.util.xml.StubbedOccurrence;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.CustomDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.stubs.AttributeStub;
import com.intellij.util.xml.stubs.ElementStub;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 8/7/12
 */
class DomStubBuilderVisitor {
  private final DomManagerImpl myManager;

  DomStubBuilderVisitor(DomManagerImpl manager) {
    myManager = manager;
  }
  
  void visitXmlElement(XmlElement element, ElementStub parent, int index) {
    DomInvocationHandler handler = myManager.getDomHandler(element);
    if (handler == null || handler.getAnnotation(Stubbed.class) == null && !handler.getChildDescription().isStubbed()) return;

    AbstractDomChildrenDescription description = handler.getChildDescription();
    String nsKey = description instanceof DomChildrenDescription ? ((DomChildrenDescription)description).getXmlName().getNamespaceKey() : "";
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;

      String elementClass = null;
      if (handler.getAnnotation(StubbedOccurrence.class) != null) {
        final Type type = description.getType();
        elementClass = ((Class)type).getName();
      }
      ElementStub stub = new ElementStub(parent,
                                         StringRef.fromString(tag.getName()),
                                         StringRef.fromNullableString(nsKey),
                                         index,
                                         description instanceof CustomDomChildrenDescription,
                                         elementClass == null ? null : StringRef.fromNullableString(elementClass),
                                         tag.getSubTags().length == 0 ? tag.getValue().getTrimmedText() : "");

      for (XmlAttribute attribute : tag.getAttributes()) {
        visitXmlElement(attribute, stub, 0);
      }
      Map<String, Integer> indices = new HashMap<>();
      for (final XmlTag subTag : tag.getSubTags()) {
        String name = subTag.getName();
        Integer i = indices.get(name);
        i = i == null ? 0 : i + 1;
        visitXmlElement(subTag, stub, i);
        indices.put(name, i);
      }
    } else if (element instanceof XmlAttribute) {
      new AttributeStub(parent, StringRef.fromString(((XmlAttribute)element).getLocalName()), 
                        StringRef.fromNullableString(nsKey), 
                        ((XmlAttribute)element).getValue());
    }
  }

}
