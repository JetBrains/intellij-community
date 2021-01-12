// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class XmlAttlistDeclImpl extends XmlElementImpl implements XmlAttlistDecl {
  private static final Logger LOG = Logger.getInstance(XmlAttlistDeclImpl.class);

  public XmlAttlistDeclImpl() {
    super(XmlElementType.XML_ATTLIST_DECL);
  }

  @Override
  public XmlElement getNameElement() {
    ASTNode child = getNode().findChildByType(XmlTokenType.XML_NAME);
    return child != null ? child.getPsi(XmlElement.class) : null;
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
    return result.toArray(new XmlAttributeDecl[0]);
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public String getName() {
    XmlElement xmlElement = getNameElement();
    if (xmlElement != null) return xmlElement.getText();

    return getNameFromEntityRef(this, XmlTokenType.XML_ATTLIST_DECL_START);
  }
}
