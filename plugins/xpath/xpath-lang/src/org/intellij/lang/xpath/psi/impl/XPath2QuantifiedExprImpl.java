/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.xpath.XPath2ElementTypes;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;

public class XPath2QuantifiedExprImpl extends XPath2ElementImpl implements XPath2QuantifiedExpr {
  public XPath2QuantifiedExprImpl(ASTNode node) {
    super(node);
  }

  @NotNull
  public XPathType getType() {
    return XPath2Type.BOOLEAN;
  }

  @NotNull
  @Override
  public XPathVariableDeclaration[] getVariables() {
    return findChildrenByClass(XPathVariableDeclaration.class);
  }

  @Override
  public XPathExpression getTest() {
    final ASTNode node = getNode().findChildByType(XPath2ElementTypes.BODY);
    return node != null ? PsiTreeUtil.findChildOfType(node.getPsi(), XPathExpression.class) : null;
  }

  public void accept(XPath2ElementVisitor visitor) {
    visitor.visitXPath2QuantifiedExpr(this);
  }
}