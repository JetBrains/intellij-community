/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.html.impl;

import com.intellij.html.index.Html5CustomAttributesIndex;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptorsProvider;
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor;
import com.intellij.xml.util.HtmlUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class Html5CustomAttributeDescriptorsProvider implements XmlAttributeDescriptorsProvider {
  @Override
  public XmlAttributeDescriptor[] getAttributeDescriptors(XmlTag tag) {
    if (tag == null || !HtmlUtil.isHtml5Context(tag)) {
      return XmlAttributeDescriptor.EMPTY;
    }
    final List<String> currentAttrs = new ArrayList<String>();
    for (XmlAttribute attribute : tag.getAttributes()) {
      currentAttrs.add(attribute.getName());
    }
    final List<XmlAttributeDescriptor> result = new ArrayList<XmlAttributeDescriptor>();
    FileBasedIndex.getInstance().processAllKeys(Html5CustomAttributesIndex.ID, new Processor<String>() {
      @Override
      public boolean process(String s) {
        boolean add = true;
        for (String attr : currentAttrs) {
          if (attr.startsWith(s)) {
            add = false;
          }
        }
        if (add) {
          result.add(new AnyXmlAttributeDescriptor(s));
        }
        return true;
      }
    }, tag.getProject());

    return result.toArray(new XmlAttributeDescriptor[result.size()]);
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, XmlTag context) {
    if (context != null && HtmlUtil.isHtml5Context(context) && HtmlUtil.isCustomHtml5Attribute(attributeName)) {
      return new AnyXmlAttributeDescriptor(attributeName);
    }
    return null;
  }

}
