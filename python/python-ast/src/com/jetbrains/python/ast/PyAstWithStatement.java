// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;


import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Experimental
public interface PyAstWithStatement extends PyAstCompoundStatement, PyAstNamedElementContainer, PyAstStatementListContainer {
  TokenSet WITH_ITEM = TokenSet.create(PyElementTypes.WITH_ITEM);

  @Override
  @NotNull
  default List<PsiNamedElement> getNamedElements() {
    PyAstWithItem[] items = PsiTreeUtil.getChildrenOfType(this, PyAstWithItem.class);
    List<PsiNamedElement> result = new ArrayList<>();
    if (items != null) {
      for (PyAstWithItem item : items) {
        PyAstExpression targetExpression = item.getTarget();
        final List<PyAstExpression> expressions = PyUtilCore.flattenedParensAndTuples(targetExpression);
        for (PyAstExpression expression : expressions) {
          if (expression instanceof PsiNamedElement) {
            result.add((PsiNamedElement)expression);
          }
        }
      }
    }
    return result;
  }

  default PyAstWithItem[] getWithItems() {
    return childrenToPsi(WITH_ITEM, PyAstWithItem.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  default PyAstStatementList getStatementList() {
    final PyAstStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
    assert statementList != null : "Statement list missing for with statement " + getText();
    return statementList;
  }

  default boolean isAsync() {
    return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
  }
}