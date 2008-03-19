package com.intellij.psi.impl.source.xml;

import com.intellij.psi.filters.position.XmlTokenTypeFilter;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlEnumeratedType;
import com.intellij.psi.xml.XmlTokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class XmlEnumeratedTypeImpl extends XmlElementImpl implements XmlEnumeratedType, XmlElementType {
  public XmlEnumeratedTypeImpl() {
    super(XML_ENUMERATED_TYPE);
  }

  public XmlElement[] getEnumeratedValues() {
    final List<XmlElement> result = new ArrayList<XmlElement>();
    processElements(new FilterElementProcessor(new XmlTokenTypeFilter(XmlTokenType.XML_NAME), result), this);
    return result.toArray(new XmlElement[result.size()]);
  }
}
