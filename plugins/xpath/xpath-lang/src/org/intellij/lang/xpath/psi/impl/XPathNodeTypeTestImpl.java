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

import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.XPathElementVisitor;
import org.intellij.lang.xpath.psi.NodeType;
import org.intellij.lang.xpath.psi.XPathNodeTypeTest;
import org.intellij.lang.xpath.psi.XPathType;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathNodeTypeTestImpl extends XPathFunctionCallImpl implements XPathNodeTypeTest {
    public XPathNodeTypeTestImpl(ASTNode node) {
        super(node);
    }

    @NotNull
    public XPathType getType() {
        return XPathType.NODESET;
    }

    public NodeType getNodeType() {
        return NodeType.valueOf(getFunctionName().replace('-', '_').toUpperCase());
    }

    @Nullable
    protected ASTNode getPrefixNode() {
        return null;
    }

    @Nullable
    protected ASTNode getNameNode() {
        return getNode().findChildByType(XPathTokenTypes.NODE_TYPE);
    }

  public void accept(final XPathElementVisitor visitor) {
    visitor.visitXPathNodeTypeTest(this);
  }
}
