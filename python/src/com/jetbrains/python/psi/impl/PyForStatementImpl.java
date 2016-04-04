/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
  public List<PsiNamedElement> getNamedElements() {
    PyExpression tgt = getForPart().getTarget();
    final List<PyExpression> expressions = PyUtil.flattenedParensAndStars(tgt);
    final List<PsiNamedElement> results = Lists.newArrayList();
    for (PyExpression expression : expressions) {
      if (expression instanceof PsiNamedElement) {
        results.add((PsiNamedElement)expression);
      }
    }
    return results;
  }

  @Override
  public boolean isAsync() {
    return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
  }
}
