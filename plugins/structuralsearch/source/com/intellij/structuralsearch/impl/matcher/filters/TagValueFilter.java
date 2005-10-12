package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTag;

/**
 * Created by IntelliJ IDEA.
 * User: maxim.mossienko
 * Date: Oct 12, 2005
 * Time: 4:44:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class TagValueFilter extends NodeFilter {
  private static NodeFilter instance;

  public void visitXmlText(XmlText text) {
    result = true;
  }

  public void visitXmlTag(XmlTag tag) {
    result = true;
  }

  public static NodeFilter getInstance() {
    if (instance==null) instance = new TagValueFilter();
    return instance;
  }

  private TagValueFilter() {
  }
}
