/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.intellij.lang.xpath.psi.XPath2ElementVisitor;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableDeclaration;
import org.jetbrains.annotations.Nullable;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 10.01.11
*/
public class XPath2VariableDeclarationImpl extends XPath2ElementImpl implements XPathVariableDeclaration {
  public XPath2VariableDeclarationImpl(ASTNode node) {
    super(node);
  }

  @Override
  public XPathExpression getInitializer() {
    return PsiTreeUtil.findChildOfType(this, XPathExpression.class);
  }

  @Nullable
  public XPathVariable getVariable() {
    return PsiTreeUtil.findChildOfType(this, XPathVariable.class);
  }

  public void accept(XPath2ElementVisitor visitor) {
    visitor.visitXPathVariableDeclaration(this);
  }
}