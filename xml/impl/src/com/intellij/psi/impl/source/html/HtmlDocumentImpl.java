package com.intellij.psi.impl.source.html;

import com.intellij.psi.impl.source.xml.XmlDocumentImpl;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;

/**
 * @author Maxim.Mossienko
 */
public class HtmlDocumentImpl extends XmlDocumentImpl {
  public HtmlDocumentImpl() {
    super(XmlElementType.HTML_DOCUMENT);
  }

  public XmlTag getRootTag() {
    return (XmlTag)findElementByTokenType(XmlElementType.HTML_TAG);
  }
}
