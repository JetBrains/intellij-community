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

import org.intellij.lang.xpath.psi.XPathBinaryExpression;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.XPathElementType;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.XPathElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.lang.ASTNode;

public class XPathBinaryExpressionImpl extends XPathElementImpl implements XPathBinaryExpression {
    public XPathBinaryExpressionImpl(ASTNode node) {
        super(node);
    }

    @Nullable
    public XPathExpression getLOperand() {
        final ASTNode[] nodes = getNode().getChildren(XPathElementTypes.EXPRESSIONS);
        return (XPathExpression)(nodes.length > 0 ? nodes[0].getPsi() : null);
    }

    @Nullable
    public XPathExpression getROperand() {
        final ASTNode[] nodes = getNode().getChildren(XPathElementTypes.EXPRESSIONS);
        return (XPathExpression)(nodes.length > 1 ? nodes[1].getPsi() : null);
    }

    @NotNull
    public XPathElementType getOperator() {
        final ASTNode[] nodes = getNode().getChildren(XPathTokenTypes.BINARY_OPERATIONS);
        final XPathElementType elementType = (XPathElementType)(nodes.length > 0 ? nodes[0].getElementType() : null);
        assert elementType != null;
        return elementType;
    }

    @NotNull
    public XPathType getType() {
        final XPathElementType operator = getOperator();
        if (operator == XPathTokenTypes.UNION) {
            return XPathType.NODESET;
        } else if (XPathTokenTypes.BOOLEAN_OPERATIONS.contains(operator)) {
            return XPathType.BOOLEAN;
        } else if (XPathTokenTypes.NUMBER_OPERATIONS.contains(operator)) {
            return XPathType.NUMBER;
        } else {
            return XPathType.UNKNOWN;
        }
    }
}