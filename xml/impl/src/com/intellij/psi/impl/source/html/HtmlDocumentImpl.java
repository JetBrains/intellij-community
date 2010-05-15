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
package com.intellij.psi.impl.source.html;

import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.impl.source.xml.XmlDocumentImpl;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;

import java.net.URL;

/**
 * @author Maxim.Mossienko
 */
public class HtmlDocumentImpl extends XmlDocumentImpl {
  private static final String HTML5_SCHEMA = "html5/xhtml5.xsd";
  
  public HtmlDocumentImpl() {
    super(XmlElementType.HTML_DOCUMENT);
  }

  public XmlTag getRootTag() {
    return (XmlTag)findElementByTokenType(XmlElementType.HTML_TAG);
  }

  @Override
  protected XmlFile getNsDescriptorWhenEmptyDocType(XmlFile containingFile) {
    URL schemaLocation = getClass().getResource(ExternalResourceManagerImpl.STANDARD_SCHEMAS + HTML5_SCHEMA);
    String path = FileUtil.toSystemIndependentName(schemaLocation.getPath().substring(1));
    return XmlUtil.findNamespace(containingFile, path);
  }
}
