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
import org.intellij.lang.xpath.XPath2ElementTypes;
import org.intellij.lang.xpath.psi.XPathElementVisitor;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathPredicate;
import org.jetbrains.annotations.Nullable;

public class XPathPredicateImpl extends XPathElementImpl implements XPathPredicate {
    public XPathPredicateImpl(ASTNode node) {
        super(node);
    }

    @Nullable
    public XPathExpression getPredicateExpression() {
        final ASTNode[] nodes = getNode().getChildren(XPath2ElementTypes.EXPRESSIONS);
        return (XPathExpression)(nodes.length > 0 ? nodes[0].getPsi() : null);
    }

  public void accept(XPathElementVisitor visitor) {
    visitor.visitXPathPredicate(this);
  }
}
