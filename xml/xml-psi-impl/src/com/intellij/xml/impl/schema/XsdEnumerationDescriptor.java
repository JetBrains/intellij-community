// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl.schema;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PairProcessor;
import com.intellij.util.SmartList;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
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
    return ArrayUtilRt.toStringArray(list);
  }

  private boolean processEnumeration(XmlElement context, PairProcessor<? super PsiElement, ? super String> processor, boolean forCompletion) {
    if (getDeclaration() == null) return false;

    XmlTag contextTag = context != null ? PsiTreeUtil.getContextOfType(context, XmlTag.class, false) : null;
    final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(getDeclaration(), contextTag);

    if (elementDescriptor!=null && elementDescriptor.getType() instanceof ComplexTypeDescriptor) {
      TypeDescriptor type = elementDescriptor.getType();
      return processEnumerationImpl(type.getDeclaration(), (ComplexTypeDescriptor)type, processor, forCompletion);
    }

    final String namespacePrefix = getDeclaration().getNamespacePrefix();
    XmlTag type = getDeclaration().findFirstSubTag(
      ((!namespacePrefix.isEmpty()) ? namespacePrefix + ":" : "") + "simpleType"
    );

    if (type != null) {
      return processEnumerationImpl(type, null, processor, forCompletion);
    }

    return false;
  }

  private boolean processEnumerationImpl(final XmlTag declaration,
                                         @Nullable ComplexTypeDescriptor type,
                                         final PairProcessor<? super PsiElement, ? super String> pairProcessor,
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
    return processEnumeration(context, PairProcessor.alwaysTrue(), false);
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

  @Override
  public boolean isList() {
    XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(getDeclaration(), null);
    if (elementDescriptor == null) return false;
    TypeDescriptor type = elementDescriptor.getType(null);
    if (!(type instanceof ComplexTypeDescriptor)) return false;
    final Ref<Boolean> result = new Ref<>(false);
    new XmlSchemaTagsProcessor(((ComplexTypeDescriptor)type).getNsDescriptor()) {
      @Override
      protected void tagStarted(XmlTag tag, String tagName, XmlTag context, @Nullable XmlTag ref) {
        if ("list".equals(tagName) || "union".equals(tagName)) {
          result.set(true);
        }
      }
    }.startProcessing(type.getDeclaration());
    return result.get();
  }
}
