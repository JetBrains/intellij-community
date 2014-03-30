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
import com.intellij.util.Processor;
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

  public String getDefaultValue() {
    if (isFixed()) {
      return getDeclaration().getAttributeValue("fixed");
    }

    return getDeclaration().getAttributeValue("default");
  }

  public boolean isFixed() {
    return getDeclaration().getAttributeValue("fixed") != null;
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
    XmlTag contextTag = context != null ? PsiTreeUtil.getContextOfType(context, XmlTag.class, false) : null;
    final XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)XmlUtil.findXmlDescriptorByType(getDeclaration(), contextTag);

    if (elementDescriptor!=null && elementDescriptor.getType() instanceof ComplexTypeDescriptor) {
      return processEnumerationImpl(((ComplexTypeDescriptor)elementDescriptor.getType()).getDeclaration(), processor);
    }

    final String namespacePrefix = getDeclaration().getNamespacePrefix();
    XmlTag type = getDeclaration().findFirstSubTag(
      ((namespacePrefix.length() > 0) ? namespacePrefix + ":" : "") + "simpleType"
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
          return name == null || pairProcessor.process(tag, name.getValue());
        }
      });
      return found.get();
    }
  }

  @Override
  public PsiElement getValueDeclaration(XmlElement attributeValue, String value) {
    PsiElement declaration = super.getValueDeclaration(attributeValue, value);
    if (declaration == null && !myExhaustiveEnum) {
      return getDeclaration();
    }
    return declaration;
  }


  public boolean isEnumerated(@Nullable XmlElement context) {
    return processEnumeration(context, PairProcessor.TRUE);
  }

  @Override
  public PsiElement getEnumeratedValueDeclaration(XmlElement xmlElement, final String value) {
    final Ref<PsiElement> result = new Ref<PsiElement>();
    processEnumeration(getDeclaration(), new PairProcessor<PsiElement, String>() {
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

  @Override
  protected PsiElement getDefaultValueDeclaration() {
    return getDeclaration();
  }
}
