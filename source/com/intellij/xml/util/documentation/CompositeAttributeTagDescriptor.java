package com.intellij.xml.util.documentation;

import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.documentation.HtmlAttributeDescriptor;

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2004
 * Time: 23:53:45
 * To change this template use File | Settings | File Templates.
 */
class CompositeAttributeTagDescriptor extends HtmlAttributeDescriptor {
  List<HtmlAttributeDescriptor> attributes = new LinkedList<HtmlAttributeDescriptor>();

  HtmlAttributeDescriptor findHtmlAttributeInContext(XmlTag tag) {
    String contextName = tag.getName();

    for (Iterator<HtmlAttributeDescriptor> iterator = attributes.iterator(); iterator.hasNext();) {
      HtmlAttributeDescriptor attributeDescriptor = iterator.next();

      if (attributeDescriptor.isValidParentTagName(contextName)) {
        return attributeDescriptor;
      }
    }

    return null;
  }
}
