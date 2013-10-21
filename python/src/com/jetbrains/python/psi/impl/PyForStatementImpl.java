/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;

public class PyForStatementImpl extends PyPartitionedElementImpl implements PyForStatement {
  public PyForStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyForStatement(this);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @NotNull
  public PyForPart getForPart() {
    return findNotNullChildByClass(PyForPart.class);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyExpression tgt = getForPart().getTarget();
    if (tgt instanceof PyReferenceExpression) return Collections.<PyElement>singleton(tgt);
    else {
      return new ArrayList<PyElement>(PyUtil.flattenedParensAndStars(tgt));
    }
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return false; 
  }
}
