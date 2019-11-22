// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author dcheryasov
 */
public class PyExceptPartImpl extends PyBaseElementImpl<PyExceptPartStub> implements PyExceptPart {
  public PyExceptPartImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyExceptPartImpl(PyExceptPartStub stub) {
    super(stub, PyElementTypes.EXCEPT_PART);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyExceptBlock(this);
  }

  @Override
  @Nullable
  public PyExpression getExceptClass() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Override
  @Nullable
  public PyExpression getTarget() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
  }

  @Override
  @NotNull
  public PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @Override
  @NotNull
  public List<PsiNamedElement> getNamedElements() {
    final List<PyExpression> expressions = PyUtil.flattenedParensAndStars(getTarget());
    final List<PsiNamedElement> results = Lists.newArrayList();
    for (PyExpression expression : expressions) {
      if (expression instanceof PsiNamedElement) {
        results.add((PsiNamedElement)expression);
      }
    }
    return results;
  }

  @Nullable
  public PsiNamedElement getNamedElement(@NotNull final String the_name) {
    // Requires switching from stubs to AST in getTarget()
    return PyUtil.IterHelper.findName(getNamedElements(), the_name);
  }
}
