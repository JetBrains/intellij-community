/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class XmlPatterns extends PlatformPatterns {
  public static XmlFilePattern.Capture xmlFile() {
    return new XmlFilePattern.Capture();
  }

  public static XmlAttributeValuePattern xmlAttributeValue(ElementPattern<? extends XmlAttribute> attributePattern) {
    return xmlAttributeValue().withParent(attributePattern);
  }

  public static XmlAttributeValuePattern xmlAttributeValue() {
    return new XmlAttributeValuePattern();
  }

  public static XmlNamedElementPattern.XmlAttributePattern xmlAttribute(@NonNls String localName) {
    return xmlAttribute().withLocalName(localName);
  }

  public static XmlNamedElementPattern.XmlAttributePattern xmlAttribute() {
    return new XmlNamedElementPattern.XmlAttributePattern();
  }

  public static XmlTagPattern.Capture xmlTag() {
    return new XmlTagPattern.Capture();
  }

  public static XmlElementPattern.XmlTextPattern xmlText() {
    return new XmlElementPattern.XmlTextPattern();
  }
}
