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
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.psi.XPath2ElementVisitor;
import org.intellij.lang.xpath.psi.XPath2If;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathType;
import org.jetbrains.annotations.NotNull;

public class XPath2IfImpl extends XPath2ElementImpl implements XPath2If {
  public XPath2IfImpl(ASTNode node) {
    super(node);
  }

  @NotNull
  public XPathType getType() {
    final XPathExpression then = getThenBranch();
    final XPathExpression value = then != null ? then : getElseBranch();
    return value != null ? value.getType() : XPathType.UNKNOWN;
  }

  @Override
  public XPathExpression getCondition() {
    return ArrayUtil.getFirstElement(findChildrenByClass(XPathExpression.class));
  }

  @Override
  public XPathExpression getThenBranch() {
    return null;
  }

  @Override
  public XPathExpression getElseBranch() {
    return null;
  }

  public void accept(XPath2ElementVisitor visitor) {
    visitor.visitXPath2If(this);
  }
}