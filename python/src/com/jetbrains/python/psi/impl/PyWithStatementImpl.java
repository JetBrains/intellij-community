// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyWithStatementImpl extends PyElementImpl implements PyWithStatement {
  private static final TokenSet WITH_ITEM = TokenSet.create(PyElementTypes.WITH_ITEM);

  public PyWithStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWithStatement(this);
  }

  @Override
  @NotNull
  public List<PsiNamedElement> getNamedElements() {
    PyWithItem[] items = PsiTreeUtil.getChildrenOfType(this, PyWithItem.class);
    List<PsiNamedElement> result = new ArrayList<>();
    if (items != null) {
      for (PyWithItem item : items) {
        PyExpression targetExpression = item.getTarget();
        final List<PyExpression> expressions = PyUtil.flattenedParensAndTuples(targetExpression);
        for (PyExpression expression : expressions) {
          if (expression instanceof PsiNamedElement) {
            result.add((PsiNamedElement)expression);
          }
        }
      }
    }
    return result;
  }

  @Nullable
  public PsiNamedElement getNamedElement(@NotNull final String the_name) {
    return PyUtil.IterHelper.findName(getNamedElements(), the_name);
  }

  @Override
  public PyWithItem[] getWithItems() {
    return childrenToPsi(WITH_ITEM, PyWithItem.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public PyStatementList getStatementList() {
    final PyStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
    assert statementList != null : "Statement list missing for with statement " + getText();
    return statementList;
  }

  @Override
  public boolean isAsync() {
    return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
  }
}
