/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    if (tag == null) return null;
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
