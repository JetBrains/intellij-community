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
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathLocationPathImpl extends XPathElementImpl implements XPathLocationPath {
    public XPathLocationPathImpl(ASTNode node) {
        super(node);
    }

    @NotNull
    public XPathType getType() {
      final XPathStep step = getFirstStep();
      if (step != null) {
        final XPathExpression expr = step.getStep();
        if (expr != null) {
          return expr.getType();
        }
      }
      return getXPathVersion() == XPathVersion.V1 ?
                XPathType.NODESET : XPath2Type.SEQUENCE;
    }

    @Nullable
    public XPathStep getFirstStep() {
      return findChildByClass(XPathStep.class);
    }

    public boolean isAbsolute() {
        final XPathStep pathExpression = getFirstStep();
        return pathExpression != null && pathExpression.isAbsolute();
    }

  public void accept(XPathElementVisitor visitor) {
    visitor.visitXPathLocationPath(this);
  }
}
