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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
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

  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWithStatement(this);
  }

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
}
