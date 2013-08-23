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
package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public class XmlAttributeDescriptorImpl extends XsdEnumerationDescriptor implements PsiWritableMetaData, XmlAttributeDescriptor {
  private XmlTag myTag;
  String myUse;

  @NonNls
  public static final String REQUIRED_ATTR_VALUE = "required";
  @NonNls
  public static final String QUALIFIED_ATTR_VALUE = "qualified";

  public XmlAttributeDescriptorImpl(XmlTag tag) {
    myTag = tag;
    myUse = myTag.getAttributeValue("use");
  }

  public XmlAttributeDescriptorImpl() {}

  public XmlTag getDeclaration(){
    return myTag;
  }

  public String getName() {
    return myTag.getAttributeValue("name");
  }

  public void init(PsiElement element){
    myTag = (XmlTag) element;
    myUse = myTag.getAttributeValue("use");
  }

  public Object[] getDependences(){
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isRequired() {
    return REQUIRED_ATTR_VALUE.equals(myUse);
  }

  private boolean hasSimpleSchemaType(@NonNls String type) {
    final String attributeValue = getType();

    if (attributeValue != null) {
      if (attributeValue.endsWith(type)) {
        final String namespacePrefix = myTag.getNamespacePrefix();

        if (namespacePrefix.length() > 0) {
          return attributeValue.equals(namespacePrefix+":"+type);
        } else {
          return attributeValue.equals(type);
        }
      }
    }

    return false;
  }

  @Nullable
  public String getType() {
    return myTag.getAttributeValue("type");
  }

  public boolean hasIdType() {
    return hasSimpleSchemaType("ID");
  }

  public boolean hasIdRefType() {
    return hasSimpleSchemaType("IDREF");
  }

  public boolean isEnumerated() {
    return isEnumerated(null);
  }

  @Nullable
  @Override
  public String validateValue(XmlElement context, String value) {
    return null;
  }

  public String getName(PsiElement context) {

    if (context == null) {
      return getName();
    }
    final String form = myTag.getAttributeValue("form");
    boolean isQualifiedAttr = QUALIFIED_ATTR_VALUE.equals(form);

    final XmlTag rootTag = (((XmlFile) myTag.getContainingFile())).getRootTag();
    assert rootTag != null;
    String targetNs = rootTag.getAttributeValue("targetNamespace");
    XmlTag contextTag = (XmlTag)context;
    String name = getName();

    boolean attributeShouldBeQualified = false;

    String contextNs = contextTag.getNamespace();
    if (targetNs != null && !contextNs.equals(targetNs)) {
      final XmlElementDescriptor xmlElementDescriptor = contextTag.getDescriptor();

      if (xmlElementDescriptor instanceof XmlElementDescriptorImpl) {
        final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)xmlElementDescriptor;
        final TypeDescriptor type = elementDescriptor.getType();

        if (type instanceof ComplexTypeDescriptor) {
          final ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;
          attributeShouldBeQualified = typeDescriptor.canContainAttribute(targetNs, null) != ComplexTypeDescriptor.CanContainAttributeType.CanNotContain;
        }

        if (!attributeShouldBeQualified && contextNs.length() == 0 && targetNs.length() > 0) {
          attributeShouldBeQualified = !targetNs.equals(elementDescriptor.getNamespace());
        }
      }
    }

    if (targetNs != null &&
        ( isQualifiedAttr ||
          QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue("attributeFormDefault")) ||
          attributeShouldBeQualified
        )
      ) {
      final String prefixByNamespace = contextTag.getPrefixByNamespace(targetNs);
      if (prefixByNamespace!= null && prefixByNamespace.length() > 0) {
        name = prefixByNamespace + ":" + name;
      }
    }

    return name;
  }

  public void setName(String name) throws IncorrectOperationException {
    NamedObjectDescriptor.setName(myTag, name);
  }
}
