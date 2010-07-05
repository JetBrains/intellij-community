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
package com.intellij.xml.util;

import com.intellij.openapi.module.Module;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.HashMap;

/**
 * @author Konstantin Bulenkov
 */
public class XsdUtil {
  public static final String ELEMENT = "element";
  public static final String NAME = "name";
  public static final String TYPE = "type";
  public static final String COMPLEX_TYPE = "complexType";

  private XsdUtil() {
  }

  @Nullable
  public static XmlFile getSchema(@NotNull String namespace, @NotNull Module module) {
    final Collection<XmlFile> files = XmlUtil.findNSFilesByURI(namespace, module.getProject(), module);
    if (files.size() > 0) {
      return files.iterator().next();
    }
    return null;
  }

  public static Map<String, XsdElement> getChildrenElements(@NotNull final XmlElement xsdElement, @NotNull final String namespace) {
    final Map<String, XsdElement> set = new HashMap<String, XsdElement>();

    xsdElement.acceptChildren(new XmlRecursiveXsdElementVisitor( XsdElementsCollectProcessors.getCollectProcessor(namespace, set)));

    return set;
  }

  public static XsdElement findXsdElement(@NotNull final XmlElement xsdElement, @NotNull final String namespace, @NotNull final String elementName) {
    return getChildrenElements(xsdElement, namespace).get(elementName);
  }

  public static Map<String, XsdElement> getDeclaredElements(@NotNull final String namespace, @NotNull Module module) {
    final XmlFile xsd = getSchema(namespace, module);
    if (xsd == null) {
      return Collections.emptyMap();
    }

    final Map<String, XsdElement> set = new HashMap<String, XsdElement>();

    xsd.accept(new XmlRecursiveXsdElementVisitor( XsdElementsCollectProcessors.getCollectAllProcessor(namespace, set)));

    return set;
  }

  public static class XsdElement {
    private final String myName;
    private final String myNamespace;
    private final XmlTag declaration;

    XsdElement(String name, String namespace, XmlTag element) {
      myName = name;
      myNamespace = namespace;
      declaration = element;
    }

    public String getName() {
      return myName;
    }

    public String getNamespace() {
      return myNamespace;
    }

    @Nullable
    public XmlTag getComplexTypeTag() {
      XmlTag tag = declaration;
      while (tag != null) {
        if (COMPLEX_TYPE.equals(tag.getLocalName())) {
          return tag;
        }
        tag = tag.getParentTag();
      }
      return null;
    }

    public XmlTag getDeclaration() {
      return declaration;
    }

    @Nullable
    public String getComplexType() {
      final XmlTag tag = getComplexTypeTag();
      return tag == null ? null : tag.getAttributeValue("name");
    }
  }

  private static abstract class XsdElementsCollectProcessors implements Processor<XmlTag> {

    public static XsdElementsCollectProcessors getCollectAllProcessor (@NotNull String namespace, @NotNull Map<String, XsdElement> set) {
       return new XsdElementsCollectProcessors(namespace, set) {
         @Override
         protected boolean isCollectChildren() {
           return true;
         }
       };
    }

    // collects first children
    public static XsdElementsCollectProcessors getCollectProcessor (@NotNull String namespace, @NotNull Map<String, XsdElement> set) {
       return new XsdElementsCollectProcessors(namespace, set) {
         @Override
         protected boolean isCollectChildren() {
           return false;
         }
       };
    }
    private final Map<String, XsdElement> mySet;
    private final String myNamespace;

    private XsdElementsCollectProcessors(@NotNull String namespace, @NotNull Map<String, XsdElement> set) {
      myNamespace = namespace;
      mySet = set;
    }

    @Override
    public boolean process(XmlTag xmlTag) {
        final XmlAttribute nameElement = xmlTag.getAttribute(NAME);
        if (nameElement != null) {
          final String name = nameElement.getValue();
          mySet.put(name, new XsdElement(name, myNamespace, xmlTag));
        }

      return isCollectChildren();
    }

    protected abstract boolean isCollectChildren() ;
  }

  private static class XmlRecursiveXsdElementVisitor extends XmlRecursiveElementVisitor {
    private Processor<XmlTag> myProcessor;

    public XmlRecursiveXsdElementVisitor(@NotNull Processor<XmlTag> processor) {
      myProcessor = processor;
    }

    @Override
    public void visitXmlTag(XmlTag tag) {
      if (ELEMENT.equals(tag.getLocalName())) {
        if (myProcessor.process(tag)) {
          super.visitXmlTag(tag);
        } else {
          return;
        }
      }
      super.visitXmlTag(tag);
    }
  }
}
