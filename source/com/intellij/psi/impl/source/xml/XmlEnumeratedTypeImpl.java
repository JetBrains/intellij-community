package com.intellij.psi.impl.source.xml;

import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEnumeratedType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.filters.position.TokenTypeFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class XmlEnumeratedTypeImpl extends XmlElementImpl implements XmlEnumeratedType {
  public XmlEnumeratedTypeImpl() {
    super(XML_ENUMERATED_TYPE);
  }

  public XmlElement[] getEnumeratedValues() {
    final List result = new ArrayList();
    processElements(new FilterElementProcessor(new TokenTypeFilter(XmlTokenType.XML_NAME), result), this);
    return (XmlElement[])result.toArray(new XmlElement[result.size()]);
  }
}
