package com.intellij.psi.impl.source.html;

import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 2, 2004
 * Time: 3:52:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlTagImpl extends XmlTagImpl implements HtmlTag {
  private XmlNSDescriptor documentDescriptor;

  public HtmlTagImpl() {
    super(HTML_TAG);
  }

  public XmlTag[] findSubTags(String name, String namespace) {
    final XmlTag[] subTags = getSubTags();
    final List<XmlTag> result = new ArrayList<XmlTag>();

    for (int i = 0; i < subTags.length; i++) {
      final XmlTag subTag = subTags[i];
      if(namespace == null){
        final String tagName = subTag.getName().toLowerCase();

        if(name == null || name.equals(tagName)){
          result.add(subTag);
        }
      }
      else if (namespace.equals(subTag.getNamespace()) &&
               (name == null || name.equals(subTag.getLocalName()))
              ) {
        result.add(subTag);
      }
    }

    return result.toArray(new XmlTag[result.size()]);
  }

  public XmlAttribute findAttribute(String name, String namespace) {
    final XmlAttribute[] attributes = getAttributes();

    for (int i = 0; i < attributes.length; i++) {
      final XmlAttribute attribute = attributes[i];

      if(namespace == null){
        final String attrName = attribute.getName().toLowerCase();

        if(name == null || name.equals(attrName)){
          return attribute;
        }
      }
      else if (namespace.equals(attribute.getNamespace()) &&
               (name == null || name.equals(attribute.getLocalName()))
              ) {
        return attribute;
      }
    }

    return null;
  }

  public String getAttributeValue(String qname) {
    qname = qname.toLowerCase();
    return super.getAttributeValue(qname);
  }

  protected void cacheOneAttributeValue(String name, String value) {
    name = name.toLowerCase();
    super.cacheOneAttributeValue(name, value);
  }

  public String getAttributeValue(String name, String namespace) {
    name = name.toLowerCase();
    return super.getAttributeValue(name, namespace);
  }

  public String getNamespace() {
    return XmlUtil.XHTML_URI;
  }

  public XmlNSDescriptor getNSDescriptor(String namespace, boolean strict) {
    if (documentDescriptor==null) {
      HtmlDocumentImpl document = PsiTreeUtil.getParentOfType(this,HtmlDocumentImpl.class);
      documentDescriptor = document.getDocumentDescriptor();
    }
    return documentDescriptor;
  }

  public String toString() {
    return "HtmlTag:" + getName();
  }
}
