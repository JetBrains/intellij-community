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
package org.intellij.lang.xpath.psi;

import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.xpath.XPathFile;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 08.04.11
*/
public class XPathElementVisitor extends PsiElementVisitor {
  public void visitXPathString(XPathString o) {
    visitXPathExpression(o);
  }

  public void visitXPathPrefixExpression(XPathPrefixExpression o) {
    visitXPathExpression(o);
  }

  public void visitXPathVariable(XPathVariable o) {
    visitXPathElement(o);
  }

  public void visitXPathToken(XPathToken o) {
    visitXPathElement(o);
  }

  public void visitXPathNodeTypeTest(XPathNodeTypeTest o) {
    visitXPathFunctionCall(o);
  }

  public void visitXPathPredicate(XPathPredicate o) {
    visitXPathElement(o);
  }

  public void visitQNameElement(QNameElement o) {
    visitXPathElement(o);
  }

  public void visitXPathNodeTest(XPathNodeTest o) {
    visitQNameElement(o);
  }

  public void visitXPathFunctionCall(XPathFunctionCall o) {
    visitXPathExpression(o);
// visitQNameElement(o);
  }

  public void visitXPathBinaryExpression(XPathBinaryExpression o) {
    visitXPathExpression(o);
  }

  public void visitXPathExpression(XPathExpression o) {
    visitXPathElement(o);
  }

  public void visitXPathParenthesizedExpression(XPathParenthesizedExpression o) {
    visitXPathExpression(o);
  }

  public void visitXPathStep(XPathStep o) {
    visitXPathExpression(o);
  }

  public void visitXPathAxisSpecifier(XPathAxisSpecifier o) {
    visitXPathElement(o);
  }

  public void visitXPathNumber(XPathNumber o) {
    visitXPathExpression(o);
  }

  public void visitXPathVariableReference(XPathVariableReference o) {
    visitXPathExpression(o);
// visitQNameElement(o);
  }

  public void visitXPathFilterExpression(XPathFilterExpression o) {
    visitXPathExpression(o);
  }

  public void visitXPathFunction(XPathFunction o) {
    visitXPathElement(o);
  }

  public void visitXPathLocationPath(XPathLocationPath o) {
    visitXPathExpression(o);
  }

  public void visitXPathElement(XPathElement o) {
    visitElement(o);
  }

  public void visitXPathFile(XPathFile file) {
    visitFile(file);
  }
}