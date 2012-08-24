/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.util.xml.Stubbed;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.io.StringRef;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.CustomDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.stubs.AttributeStub;
import com.intellij.util.xml.stubs.ElementStub;
import com.intellij.util.xml.stubs.FileStub;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 8/7/12
 */
public class DomStubBuilderVisitor implements DomElementVisitor {

  private ElementStub myRoot;

  public DomStubBuilderVisitor(FileStub fileStub) {
    myRoot = fileStub;
  }

  @Override
  public void visitDomElement(DomElement element) {

    if (element.getAnnotation(Stubbed.class) != null ||
        element.getChildDescription().isStubbed()) {

      XmlElement xmlElement = element.getXmlElement();
      AbstractDomChildrenDescription description = element.getChildDescription();
      String nsKey = description instanceof DomChildrenDescription ? ((DomChildrenDescription)description).getXmlName().getNamespaceKey() : "";
      if (xmlElement instanceof XmlTag) {
        ElementStub old = myRoot;
        myRoot = new ElementStub(myRoot,
                                 StringRef.fromString(((XmlTag)xmlElement).getName()),
                                 StringRef.fromNullableString(nsKey),
                                 description instanceof CustomDomChildrenDescription);
        List<DomElement> children = DomUtil.getDefinedChildren(element, true, true);
        for (DomElement child : children) {
          visitDomElement(child);
        }
        if (old != null) {
          myRoot = old;
        }
      }
      else if (xmlElement instanceof XmlAttribute) {
        new AttributeStub(myRoot, StringRef.fromString(((XmlAttribute)xmlElement).getLocalName()),
                          StringRef.fromNullableString(nsKey),
                          ((XmlAttribute)xmlElement).getValue());
      }
    }
  }
}
