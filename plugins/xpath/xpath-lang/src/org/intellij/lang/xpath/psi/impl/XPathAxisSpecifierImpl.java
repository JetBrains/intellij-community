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

import org.intellij.lang.xpath.psi.XPathElementVisitor;
import org.intellij.lang.xpath.psi.XPathAxisSpecifier;
import org.intellij.lang.xpath.psi.Axis;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

public class XPathAxisSpecifierImpl extends XPathElementImpl implements XPathAxisSpecifier {
    public XPathAxisSpecifierImpl(ASTNode node) {
        super(node);
    }

    @NotNull
    public Axis getAxis() {
        final ASTNode[] nodes = getNode().getChildren(XPathTokenTypes.AXIS);
        if (nodes.length > 0) {
          return Axis.fromName(nodes[0].getText());
        } else if (getNode().findChildByType(XPathTokenTypes.AT) != null) {
            return Axis.ATTRIBUTE;
        } else {
            return Axis.CHILD;
        }
    }

    public boolean isDefaultAxis() {
        final ASTNode node = getNode();
        final boolean b = node.getChildren(XPathTokenTypes.AXIS).length == 0;
        return b && node.findChildByType(XPathTokenTypes.AT) == null;
    }

  public void accept(XPathElementVisitor visitor) {
    visitor.visitXPathAxisSpecifier(this);
  }
}