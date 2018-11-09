// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @Override
  @NotNull
  public PyForPart getForPart() {
    return findNotNullChildByClass(PyForPart.class);
  }

  @Override
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
