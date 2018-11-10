// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.xpath.psi.XPath2ElementVisitor;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableDeclaration;
import org.jetbrains.annotations.Nullable;

public class XPath2VariableDeclarationImpl extends XPath2ElementImpl implements XPathVariableDeclaration {
  public XPath2VariableDeclarationImpl(ASTNode node) {
    super(node);
  }

  @Override
  public XPathExpression getInitializer() {
    return PsiTreeUtil.findChildOfType(this, XPathExpression.class);
  }

  @Override
  @Nullable
  public XPathVariable getVariable() {
    return PsiTreeUtil.findChildOfType(this, XPathVariable.class);
  }

  @Override
  public void accept(XPath2ElementVisitor visitor) {
    visitor.visitXPathVariableDeclaration(this);
  }
}