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
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.*;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class XmlNotationDeclImpl extends XmlElementImpl implements XmlNotationDecl, XmlElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlElementDeclImpl");

  public XmlNotationDeclImpl() {
    super(XML_NOTATION_DECL);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XML_ELEMENT_CONTENT_SPEC) {
      return XmlChildRole.XML_ELEMENT_CONTENT_SPEC;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(XmlChildRole.XML_NAME);
  }

  @Override
  public XmlElementContentSpec getContentSpecElement() {
    return (XmlElementContentSpec)findChildByRoleAsPsiElement(XmlChildRole.XML_ELEMENT_CONTENT_SPEC);
  }
}
