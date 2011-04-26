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

public class XPath2SequenceImpl extends XPath2ElementImpl implements XPath2Sequence {
  public XPath2SequenceImpl(ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public XPathExpression[] getSequence() {
    return findChildrenByClass(XPathExpression.class);
  }

  @NotNull
  @Override
  public XPathType getType() {
    final XPathExpression[] sequence = getSequence();
    if (sequence.length == 0) {
      return XPath2Type.SEQUENCE;
    }

    XPathType commonType = XPath2Type.mapType(sequence[0].getType());
    outer:
    while (commonType != null) {
      for (int i = 1; i < sequence.length; i++) {
        final XPathType t = XPath2Type.mapType(sequence[i].getType());
        if (commonType.isAssignableFrom(t)) {
          break  outer;
        }
      }
      commonType = XPathType.getSuperType(commonType);
    }
    if (commonType != null) {
      return XPath2SequenceType.create(commonType, XPath2SequenceType.Cardinality.ONE_OR_MORE);
    }
    return XPathType.UNKNOWN;
  }

  public void accept(XPath2ElementVisitor visitor) {
    visitor.visitXPath2Sequence(this);
  }
}