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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class XmlAttlistDeclImpl extends XmlElementImpl implements XmlAttlistDecl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlAttlistDeclImpl");

  public XmlAttlistDeclImpl() {
    super(XmlElementType.XML_ATTLIST_DECL);
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XmlTokenType.XML_NAME) {
      return XmlChildRole.XML_NAME;
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
  public XmlAttributeDecl[] getAttributeDecls() {
    final List<XmlAttributeDecl> result = new ArrayList<>();
    processElements(new FilterElementProcessor(new ClassFilter(XmlAttributeDecl.class), result) {
      @Override
      public boolean execute(@NotNull final PsiElement element) {
        if (element instanceof XmlAttributeDecl) {
          if (element.getNextSibling() == null && element.getChildren().length == 1) {
            return true;
          }
          return super.execute(element);
        }
        return true;
      }
    }, this);
    return result.toArray(new XmlAttributeDecl[result.size()]);
  }

  @Override
  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this,XmlAttlistDecl.class);
  }

  @Override
  public String getName() {
    XmlElement xmlElement = getNameElement();
    if (xmlElement != null) return xmlElement.getText();

    return getNameFromEntityRef(this, XmlTokenType.XML_ATTLIST_DECL_START);
  }
}
