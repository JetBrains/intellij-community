package com.intellij.psi.impl.source.html;

import com.intellij.psi.impl.source.xml.XmlDocumentImpl;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 2, 2004
 * Time: 8:02:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlDocumentImpl extends XmlDocumentImpl {
  public HtmlDocumentImpl() {
    super(HTML_DOCUMENT);
  }

  public XmlTag getRootTag() {
    return (XmlTag)findElementByTokenType(HTML_TAG);
  }
}
