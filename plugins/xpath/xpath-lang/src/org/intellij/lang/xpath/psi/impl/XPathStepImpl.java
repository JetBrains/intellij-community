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
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.xpath.XPath2ElementTypes;
import org.intellij.lang.xpath.XPathElementTypes;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathStepImpl extends XPathElementImpl implements XPathStep {

  public XPathStepImpl(ASTNode node) {
    super(node);
  }

  @NotNull
  public XPathType getType() {
    if (getNode().getElementType() == XPath2ElementTypes.CONTEXT_ITEM) {
      final XPathPredicate predicate = PsiTreeUtil.getParentOfType(this, XPathPredicate.class);
      if (predicate != null) {
        final PsiElement filter = predicate.getParent();
        if (filter instanceof XPathFilterExpression) {
          final XPathExpression expression = ((XPathFilterExpression)filter).getExpression();
          if (expression != null) {
            final XPathType type = expression.getType();
            return (type instanceof XPath2SequenceType ? ((XPath2SequenceType)type).getType() : type);
          }
        }
      }
      return XPath2Type.SEQUENCE;
    }

    final XPathExpression step = getStep();
    if (step != null) {
      return step.getType();
    }
    return XPathType.NODESET;
  }

  public XPathAxisSpecifier getAxisSpecifier() {
    final ASTNode node = getNode().findChildByType(XPathElementTypes.AXIS_SPECIFIER);
    if (node != null) {
      return (XPathAxisSpecifier)node.getPsi();
    } else {
      return findFromXPath2FilterExpr(XPathElementTypes.AXIS_SPECIFIER, XPathAxisSpecifier.class);
    }
  }

  @Nullable
  private <T extends PsiElement> T findFromXPath2FilterExpr(IElementType type, Class<T> clazz) {
    final XPathExpression f = getPreviousStep();
    if (f instanceof XPathFilterExpression) {
      final ASTNode node = f.getNode();
      final ASTNode child = node.findChildByType(type);
      return child != null ? child.getPsi(clazz) : null;
    }
    return null;
  }

  public XPathNodeTest getNodeTest() {
    final ASTNode node = getNode().findChildByType(XPathElementTypes.NODE_TEST);
    if (node != null) {
      return node.getPsi(XPathNodeTest.class);
    } else {
      return findFromXPath2FilterExpr(XPathElementTypes.NODE_TEST, XPathNodeTest.class);
    }
  }

  @NotNull
  public XPathPredicate[] getPredicates() {
    final ASTNode[] nodes = getNode().getChildren(XPathElementTypes.PREDICATES);
    final XPathPredicate[] predicates = new XPathPredicate[nodes.length];
    for (int i = 0; i < predicates.length; i++) {
      predicates[i] = (XPathPredicate)nodes[i].getPsi();
    }
    return predicates;
  }

  @Nullable
  public XPathExpression getPreviousStep() {
    final XPathExpression[] nodes = findChildrenByClass(XPathExpression.class);

    if (nodes.length > 0) {
      return nodes[0];
    }
    return null;
  }

  @Nullable
  public XPathExpression getStep() {
    final XPathExpression[] nodes = findChildrenByClass(XPathExpression.class);

    if (nodes.length > 1) {
      return nodes[1];
    }
    return null;
  }

  public boolean isAbsolute() {
    return getPreviousStep() == null && getNode().getChildren(XPathTokenTypes.PATH_OPS).length > 0;
  }

  public void accept(XPathElementVisitor visitor) {
    visitor.visitXPathStep(this);
  }
}
