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
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;

public class XPath2RangeExpressionImpl extends XPathElementImpl implements XPath2RangeExpression {
  public XPath2RangeExpressionImpl(ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public XPathExpression getFrom() {
    return findChildrenByClass(XPathExpression.class)[0];
  }

  @Override
  public XPathExpression getTo() {
    XPathExpression[] expressions = findChildrenByClass(XPathExpression.class);
    return expressions.length > 1 ? expressions[1] : null;
  }

  @NotNull
  @Override
  public XPathType getType() {
    return XPath2SequenceType.create(XPath2Type.INTEGER, XPath2SequenceType.Cardinality.ZERO_OR_MORE);
  }
}