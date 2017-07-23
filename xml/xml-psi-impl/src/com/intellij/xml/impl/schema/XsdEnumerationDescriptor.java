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
package com.intellij.xml.impl.schema;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.SmartList;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 22.08.13
 */
public abstract class XsdEnumerationDescriptor<T extends XmlElement> extends XmlEnumerationDescriptor<T> {

  private boolean myExhaustiveEnum;

  public abstract XmlTag getDeclaration();

  @Override
  public String getDefaultValue() {
    if (isFixed()) {
      return getDeclaration().getAttributeValue("fixed");
    }

    return getDeclaration().getAttributeValue("default");
  }

  @Override
  public boolean isFixed() {
    return getDeclaration().getAttributeValue("fixed") != null;
  }

  @Override
  public String[] getEnumeratedValues() {
    return getEnumeratedValues(false);
  }

  @Override
  public String[] getValuesForCompletion() {
    return getEnumeratedValues(true);
  }

  private String[] getEnumeratedValues(boolean forCompletion) {
    final List<String> list = new SmartList<>();
    processEnumeration(null, (element, s) -> {
      list.add(s);
      return true;
    }, forCompletion);
    String defaultValue = getDefaultValue();
    if (defaultValue != null) {
      list.add(defaultValue);
    }
    return ArrayUtil.toStringArray(list);
  }

  private boolean processEnumeration(XmlElement context, PairProcessor<PsiElement, String> processor, boolean forCompletion) {
    XmlTag contextTag = context != null ? PsiTreeUtil.getContextOfType(context, XmlTag.class, false) : null;
    final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(getDeclaration(), contextTag);

    if (elementDescriptor!=null && elementDescriptor.getType() instanceof ComplexTypeDescriptor) {
      TypeDescriptor type = elementDescriptor.getType();
      return processEnumerationImpl(type.getDeclaration(), (ComplexTypeDescriptor)type, processor, forCompletion);
    }

    final String namespacePrefix = getDeclaration().getNamespacePrefix();
    XmlTag type = getDeclaration().findFirstSubTag(
      ((namespacePrefix.length() > 0) ? namespacePrefix + ":" : "") + "simpleType"
    );

    if (type != null) {
      return processEnumerationImpl(type, null, processor, forCompletion);
    }

    return false;
  }

  private boolean processEnumerationImpl(final XmlTag declaration,
                                         @Nullable ComplexTypeDescriptor type,
                                         final PairProcessor<PsiElement, String> pairProcessor,
                                         boolean forCompletion) {
    XmlAttribute name = declaration.getAttribute("name");
    if (name != null && "boolean".equals(name.getValue()) && type != null) {
      XmlNSDescriptorImpl nsDescriptor = type.getNsDescriptor();
      if (nsDescriptor != null) {
        String namespace = nsDescriptor.getDefaultNamespace();
        if (XmlUtil.XML_SCHEMA_URI.equals(namespace)) {
          XmlAttributeValue valueElement = name.getValueElement();
          pairProcessor.process(valueElement, "true");
          pairProcessor.process(valueElement, "false");
          if (!forCompletion) {
            pairProcessor.process(valueElement, "1");
            pairProcessor.process(valueElement, "0");
          }
          myExhaustiveEnum = true;
          return true;
        }
      }
    }

    final Ref<Boolean> found = new Ref<>(Boolean.FALSE);
    myExhaustiveEnum = XmlUtil.processEnumerationValues(declaration, tag -> {
      found.set(Boolean.TRUE);
      XmlAttribute name1 = tag.getAttribute("value");
      return name1 == null || pairProcessor.process(tag, name1.getValue());
    });
    return found.get();
  }

  @Override
  public PsiElement getValueDeclaration(XmlElement attributeValue, String value) {
    PsiElement declaration = super.getValueDeclaration(attributeValue, value);
    if (declaration == null && !myExhaustiveEnum) {
      return getDeclaration();
    }
    return declaration;
  }


  @Override
  public boolean isEnumerated(@Nullable XmlElement context) {
    return processEnumeration(context, PairProcessor.TRUE, false);
  }

  @Override
  public PsiElement getEnumeratedValueDeclaration(XmlElement xmlElement, final String value) {
    final Ref<PsiElement> result = new Ref<>();
    processEnumeration(getDeclaration(), (element, s) -> {
      if (value.equals(s)) {
        result.set(element);
        return false;
      }
      return true;
    }, false);
    return result.get();
  }

  @Override
  protected PsiElement getDefaultValueDeclaration() {
    return getDeclaration();
  }
}
