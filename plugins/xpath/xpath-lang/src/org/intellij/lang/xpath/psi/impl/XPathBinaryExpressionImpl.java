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
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.intellij.lang.xpath.*;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathBinaryExpressionImpl extends XPathElementImpl implements XPathBinaryExpression {
    private static final TokenSet BINARY_OPERATIONS = TokenSet.orSet(XPathTokenTypes.BINARY_OPERATIONS,
          XPath2TokenTypes.COMP_OPS,
          XPath2TokenTypes.MULT_OPS,
          TokenSet.create(XPath2TokenTypes.TO, XPath2TokenTypes.INSTANCE, XPath2TokenTypes.EXCEPT, XPath2TokenTypes.INTERSECT, XPath2TokenTypes.UNION));

    public XPathBinaryExpressionImpl(ASTNode node) {
        super(node);
    }

    @Nullable
    public XPathExpression getLOperand() {
        final ASTNode[] nodes = getNode().getChildren(XPath2ElementTypes.EXPRESSIONS);
        return (XPathExpression)(nodes.length > 0 ? nodes[0].getPsi() : null);
    }

    @Nullable
    public XPathExpression getROperand() {
        final ASTNode[] nodes = getNode().getChildren(XPath2ElementTypes.EXPRESSIONS);
        return (XPathExpression)(nodes.length > 1 ? nodes[1].getPsi() : null);
    }

    @NotNull
    public XPathElementType getOperator() {
        final ASTNode[] nodes = getNode().getChildren(BINARY_OPERATIONS);
        final XPathElementType elementType = (XPathElementType)(nodes.length > 0 ? nodes[0].getElementType() : null);
        assert elementType != null : unexpectedPsiAssertion();
        return elementType;
    }

    @NotNull
    @Override
    public String getOperationSign() {
      final ASTNode[] nodes = getNode().getChildren(BINARY_OPERATIONS);
      return nodes[0].getText();
    }

    @NotNull
    public XPathType getType() {
        return CachedValuesManager.getCachedValue(this, () ->
          CachedValueProvider.Result.create(calcType(), PsiModificationTracker.MODIFICATION_COUNT));
    }
    @NotNull
    private XPathType calcType() {
        final XPathElementType operator = getOperator();
        if (operator == XPathTokenTypes.UNION || XPath2TokenTypes.INTERSECT_EXCEPT.contains(operator)) {
            return XPathType.NODESET;
        } else if (XPath2TokenTypes.BOOLEAN_OPERATIONS.contains(operator)) {
            return XPathType.BOOLEAN;
        } else if (operator == XPath2TokenTypes.IDIV) {
            return XPath2Type.INTEGER;
        } else if (XPath2TokenTypes.NUMBER_OPERATIONS.contains(operator)) {
          final XPathExpression lop = getLOperand();
          final XPathExpression rop = getROperand();
          if (is(lop, XPathType.UNKNOWN) || is(rop, XPathType.UNKNOWN)) {
            return XPathType.UNKNOWN;
          }

          if (XPathTokenTypes.MUL_OPS.contains(operator)) {
            if (operator == XPathTokenTypes.DIV) {
              if (is(lop, XPath2Type.INTEGER) && is(rop, XPath2Type.INTEGER)) {
                return XPath2Type.DECIMAL;
              }
              return mostSpecificType(lop, rop, XPath2Type.NUMERIC);
            }

            if (is(lop, XPath2Type.DURATION)) {
              return lop != null ? lop.getType() : XPath2Type.DURATION;
            } else if (is(rop, XPath2Type.DURATION)) {
              return lop != null ? rop.getType() : XPath2Type.DURATION;
            }

            return mostSpecificType(lop, rop, XPathType.NUMBER);
          } else {
            if (operator == XPathTokenTypes.PLUS) {
              if (is(lop, XPath2Type.DATE) || is(lop, XPath2Type.DATETIME) || is(lop, XPath2Type.TIME)) {
                if (is(rop, XPath2Type.DURATION)) {
                  return lop.getType();
                }
              } else if (is(rop, XPath2Type.DATE) || is(rop, XPath2Type.DATETIME) || is(rop, XPath2Type.TIME)) {
                if (is(lop, XPath2Type.DURATION)) {
                  return rop.getType();
                }
              }
            } else if (operator == XPathTokenTypes.MINUS) {
              if (is(lop, XPath2Type.DATE) || is(lop, XPath2Type.DATETIME) || is(lop, XPath2Type.TIME)) {
                if (is(rop, lop.getType())) {
                  return XPath2Type.DAYTIMEDURATION;
                }
                if (is(rop, XPath2Type.DURATION)) {
                  return lop.getType();
                }
              }
            }

            if (is(lop, XPath2Type.DURATION)) {
              return rop != null ? rop.getType() : XPathType.UNKNOWN;
            }
          }

          return mostSpecificType(lop, rop, XPathType.NUMBER);
        } else {
            return XPathType.UNKNOWN;
        }
    }

  private static XPathType mostSpecificType(XPathExpression lop, XPathExpression rop, XPathType type) {
    XPathType lType = lop != null ? lop.getType() : XPathType.UNKNOWN;
    XPathType rType = rop != null ? rop.getType() : XPathType.UNKNOWN;
    if (lType.isAbstract()) {
      if (rType.isAbstract()) {
        return type;
      } else {
        return rType;
      }
    } else {
      if (!rType.isAbstract()) {
        if (lType.canBePromotedTo(rType)) return rType;
        if (rType.canBePromotedTo(lType)) return lType;
        if (XPathType.isAssignable(lType, rType)) return rType;
      }
      return lType;
    }
  }

  private static boolean is(XPathExpression op, XPathType type) {
    return op != null && (type instanceof XPath2Type ? type.isAssignableFrom(op.getType()) : type == op.getType());
  }

  public void accept(XPathElementVisitor visitor) {
    visitor.visitXPathBinaryExpression(this);
  }
}