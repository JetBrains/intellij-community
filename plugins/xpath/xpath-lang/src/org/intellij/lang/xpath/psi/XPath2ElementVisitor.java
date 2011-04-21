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

import org.intellij.lang.xpath.psi.impl.XPath2ElementImpl;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 08.04.11
*/
public class XPath2ElementVisitor extends XPathElementVisitor {
  public void visitXPath2TypeReference(XPath2TypeReference o) {
    visitXPathElement(o);
  }

  public void visitXPath2InstanceOf(XPath2InstanceOf o) {
    visitXPathExpression(o);
// visitXPath2TypeReference(o);
  }

  public void visitXPath2QuantifiedExpr(XPath2QuantifiedExpr o) {
    visitXPathExpression(o);
// visitXPathVariableDeclaration(o);
  }

  public void visitXPath2TreatAs(XPath2TreatAs o) {
    visitXPathExpression(o);
// visitXPath2TypeReference(o);
  }

  public void visitXPath2If(XPath2If o) {
    visitXPathExpression(o);
  }

  public void visitXPath2Cast(XPath2Cast o) {
    visitXPathExpression(o);
// visitXPath2TypeReference(o);
  }

  public void visitXPathVariableDeclaration(XPathVariableDeclaration o) {
    visitXPathElement(o);
  }

  public void visitXPath2Sequence(XPath2Sequence o) {
    visitXPathExpression(o);
  }

  public void visitXPath2For(XPath2For o) {
    visitXPathExpression(o);
// visitXPathVariableDeclaration(o);
  }

  public void visitXPath2TypeElement(XPath2TypeElement o) {
    visitQNameElement(o);
  }

  public void visitXPath2Castable(XPath2Castable o) {
    visitXPathExpression(o);
// visitXPath2TypeReference(o);
  }

  public void visitXPath2RangeExpression(XPath2RangeExpression o) {
    visitXPathBinaryExpression(o);
  }

  @Override
  public void visitXPathElement(XPathElement o) {
    if (o instanceof XPath2ElementImpl) {
      visitXPath2Element(o);
    } else {
      super.visitXPathElement(o);
    }
  }

  public void visitXPath2Element(XPathElement element) {
    super.visitXPathElement(element);
  }
}