package com.intellij.psi.impl.source.html;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 2, 2004
 * Time: 3:52:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlTagImpl extends XmlTagImpl implements HtmlTag {
  public HtmlTagImpl() {
    super(HTML_TAG);
  }

  public XmlTag[] findSubTags(String name, String namespace) {
    final XmlTag[] subTags = getSubTags();
    final List<XmlTag> result = new ArrayList<XmlTag>();

    for (final XmlTag subTag : subTags) {
      if (namespace == null) {
        String tagName = subTag.getName();
        if (tagName == null) continue;
        tagName = tagName.toLowerCase();

        if (name == null || name.equals(tagName)) {
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

  public XmlAttribute getAttribute(String name, String namespace) {
    final XmlAttribute[] attributes = getAttributes();
    name = name.toLowerCase();

    for (final XmlAttribute attribute : attributes) {
      if (attribute.getName().toLowerCase().equals(name)) {
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

  @NotNull
  public String getNamespace() {
    if(getNamespacePrefix().length() == 0)
      return XmlUtil.HTML_URI;
    return super.getNamespace();
  }

  public String toString() {
    return "HtmlTag:" + getName();
  }

  @NotNull
  public Language getLanguage() {
    return StdFileTypes.HTML.getLanguage();
  }

  public String getPrefixByNamespace(String namespace) {
    if (namespace.equals(XmlUtil.HTML_URI)) namespace = XmlUtil.XHTML_URI;
    return super.getPrefixByNamespace(namespace);
  }
}
