// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.psi.xml.XmlAttribute;

/**
 * @author peter
 */
public class XmlPatterns extends PlatformPatterns {
  public static XmlFilePattern.Capture xmlFile() {
    return new XmlFilePattern.Capture();
  }

  public static <T extends XmlAttribute> XmlAttributeValuePattern xmlAttributeValue(ElementPattern<T> attributePattern) {
    for (PatternCondition<? super T> condition : attributePattern.getCondition().getConditions()) {
      if (condition instanceof PsiNamePatternCondition && "withLocalName".equals(condition.getDebugMethodName())) {
        return xmlAttributeValue().withLocalName(((PsiNamePatternCondition<?>)condition).getNamePattern()).withParent(attributePattern);
      }
    }

    return xmlAttributeValue().withParent(attributePattern);
  }

  public static XmlAttributeValuePattern xmlAttributeValue(String... localNames) {
    return xmlAttributeValue().withLocalName(localNames);
  }

  public static XmlAttributeValuePattern xmlAttributeValue() {
    return XmlAttributeValuePattern.XML_ATTRIBUTE_VALUE_PATTERN;
  }

  public static XmlNamedElementPattern.XmlAttributePattern xmlAttribute(String localName) {
    return xmlAttribute().withLocalName(localName);
  }

  public static XmlNamedElementPattern.XmlAttributePattern xmlAttribute() {
    return new XmlNamedElementPattern.XmlAttributePattern();
  }

  public static XmlTagPattern.Capture xmlTag() {
    return XmlTagPattern.Capture.XML_TAG_PATTERN;
  }

  public static XmlElementPattern.XmlTextPattern xmlText() {
    return new XmlElementPattern.XmlTextPattern();
  }

  public static XmlElementPattern.XmlEntityRefPattern xmlEntityRef() {
    return new XmlElementPattern.XmlEntityRefPattern();
  }
}
