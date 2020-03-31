/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.impl.source.xml.XmlDocumentImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.IXmlTagElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlPsiUtil;

/**
 * @author Maxim.Mossienko
 */
public class HtmlDocumentImpl extends XmlDocumentImpl {
  public HtmlDocumentImpl() {
    super(XmlElementType.HTML_DOCUMENT);
  }

  public HtmlDocumentImpl(IElementType type) {
    super(type);
  }

  @Override
  public XmlTag getRootTag() {
    return (XmlTag)XmlPsiUtil.findElement(this, IXmlTagElementType.class::isInstance);
  }
}
