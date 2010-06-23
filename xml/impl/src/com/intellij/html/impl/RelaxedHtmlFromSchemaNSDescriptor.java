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
package com.intellij.html.impl;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RelaxedHtmlFromSchemaNSDescriptor extends XmlNSDescriptorImpl {
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(tag);

    String namespace;
    if (elementDescriptor == null && 
        !((namespace = tag.getNamespace()).equals(XmlUtil.XHTML_URI))) {
      return new AnyXmlElementDescriptor(
        null, 
        XmlUtil.HTML_URI.equals(namespace) ? this : tag.getNSDescriptor(tag.getNamespace(), true)
      );
    }

    return elementDescriptor;
  }

  protected XmlElementDescriptor createElementDescriptor(final XmlTag tag) {
    return new RelaxedHtmlFromSchemaElementDescriptor(tag);
  }

  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument doc) {
    return ArrayUtil.mergeArrays(super.getRootElementsDescriptors(doc), HtmlUtil.getCustomTagDescriptors(doc), XmlElementDescriptor.class);
  }
}
