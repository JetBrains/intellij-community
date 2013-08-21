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

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.*;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mike
 */
public class XmlAttributeDescriptorImpl extends BasicXmlAttributeDescriptor implements PsiWritableMetaData {
  private XmlTag myTag;
  String myUse;
  private boolean myExhaustiveEnum;

  @NonNls
  public static final String REQUIRED_ATTR_VALUE = "required";
  @NonNls
  public static final String QUALIFIED_ATTR_VALUE = "qualified";

  public XmlAttributeDescriptorImpl(XmlTag tag) {
    myTag = tag;
    myUse = myTag.getAttributeValue("use");
  }

  public XmlAttributeDescriptorImpl() {}

  public PsiElement getDeclaration(){
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

  public boolean isFixed() {
    return myTag.getAttributeValue("fixed") != null;
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

  public String getDefaultValue() {
    if (isFixed()) {
      return myTag.getAttributeValue("fixed");
    }

    return myTag.getAttributeValue("default");
  }

  public boolean isEnumerated(@Nullable XmlElement context) {
    return processEnumeration(context, PairProcessor.TRUE);
  }

  public boolean isEnumerated() {
    return isEnumerated(null);
  }

  public String[] getEnumeratedValues() {
    return getEnumeratedValues(null);
  }

  public String[] getEnumeratedValues(XmlElement context) {
    final List<String> list = new SmartList<String>();
    processEnumeration(context, new PairProcessor<PsiElement, String>() {
      @Override
      public boolean process(PsiElement element, String s) {
        list.add(s);
        return true;
      }
    });
    String defaultValue = getDefaultValue();
    if (defaultValue != null) {
      list.add(defaultValue);
    }
    return ArrayUtil.toStringArray(list);
  }

  private boolean processEnumeration(XmlElement context, PairProcessor<PsiElement, String> processor) {
    XmlTag contextTag = context != null ? PsiTreeUtil.getContextOfType(context, XmlTag.class, true) : null;
    final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(myTag, contextTag);

    if (elementDescriptor!=null && elementDescriptor.getType() instanceof ComplexTypeDescriptor) {
      return processEnumerationImpl(((ComplexTypeDescriptor)elementDescriptor.getType()).getDeclaration(), processor);
    }

    final String namespacePrefix = myTag.getNamespacePrefix();
    XmlTag type = myTag.findFirstSubTag(
      ((namespacePrefix.length() > 0)?namespacePrefix+":":"")+"simpleType"
    );

    if (type != null) {
      return processEnumerationImpl(type, processor);
    }

    return false;
  }

  private boolean processEnumerationImpl(final XmlTag declaration, final PairProcessor<PsiElement, String> pairProcessor) {
    if ("boolean".equals(declaration.getAttributeValue("name"))) {
      XmlAttributeValue valueElement = declaration.getAttribute("name").getValueElement();
      pairProcessor.process(valueElement, "true");
      pairProcessor.process(valueElement, "false");
      myExhaustiveEnum = true;
      return true;
    }

    else {
      final Ref<Boolean> found = new Ref<Boolean>(Boolean.FALSE);
      myExhaustiveEnum = XmlUtil.processEnumerationValues(declaration, new Processor<XmlTag>() {
        @Override
        public boolean process(XmlTag tag) {
          found.set(Boolean.TRUE);
          XmlAttribute name = tag.getAttribute("value");
          return name == null || pairProcessor.process(name.getValueElement(), name.getValue());
        }
      });
      return found.get();
    }
  }

  @Override
  public PsiElement getValueDeclaration(XmlAttributeValue attributeValue, String value) {
    PsiElement declaration = super.getValueDeclaration(attributeValue, value);
    if (declaration == null && !myExhaustiveEnum) {
      return getDeclaration();
    }
    return declaration;
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

  @Override
  protected PsiElement getEnumeratedValueDeclaration(XmlAttributeValue attributeValue, final String value) {
    final Ref<PsiElement> result = new Ref<PsiElement>();
    processEnumeration(myTag, new PairProcessor<PsiElement, String>() {
      @Override
      public boolean process(PsiElement element, String s) {
        if (value.equals(s)) {
          result.set(element);
          return false;
        }
        return true;
      }
    });
    return result.get();
  }
}
