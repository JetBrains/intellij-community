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

import com.intellij.lang.ASTNode;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElementType;

/**
 * @author maxim
 */
public class HtmlFileImpl extends XmlFileImpl {
  public HtmlFileImpl(FileViewProvider provider) {
    super(provider, XmlElementType.HTML_FILE);
  }

  public String toString() {
    return "HtmlFile:" + getName();
  }

  @Override
  public XmlDocument getDocument() {
    CompositeElement treeElement = calcTreeElement();

    ASTNode node = treeElement.findChildByType(XmlElementType.HTML_DOCUMENT);
    return node != null ? (XmlDocument)node.getPsi() : null;
  }
}
