// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class PyKeywordArgumentImpl extends PyElementImpl implements PyKeywordArgument {
  public PyKeywordArgumentImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ": " + getKeyword();
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression e = getValueExpression();
    return e != null ? context.getType(e) : null;
  }

  @Override
  public PsiReference getReference() {
    final ASTNode keywordNode = getKeywordNode();
    if (keywordNode != null) {
      return new PyKeywordArgumentReference(this, keywordNode.getPsi().getTextRangeInParent());
    }
    return null;
  }

  @Override
  public String getName() {
    return PyKeywordArgument.super.getName();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PyElementGenerator generator = PyElementGenerator.getInstance(getProject());
    PyExpression expression = getValueExpression();
    PyKeywordArgument keywordArgument = generator.createKeywordArgument(LanguageLevel.forElement(this), name,
                                                                        expression != null ? expression.getText() : name);
    getNode().replaceChild(getKeywordNode(), keywordArgument.getKeywordNode());
    return this;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyKeywordArgument(this);
  }
}
