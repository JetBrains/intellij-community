/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyStatementList;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 16:39:47
 * To change this template use File | Settings | File Templates.
 */
public class PyStatementListImpl extends PyElementImpl implements PyStatementList {
  public PyStatementListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStatementList(this);
  }

  @PsiCached
  public PyStatement[] getStatements() {
    return childrenToPsi(PyElementTypes.STATEMENTS, PyStatement.EMPTY_ARRAY);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    // in if statements, process only the section in which the original expression was located
    PsiElement parent = getParent();
    if (parent instanceof PyStatement && lastParent == null && PsiTreeUtil.isAncestor(parent, place, true)) {
      return true;
    }

    PyStatement[] statements = getStatements();
    if (lastParent != null) {
      // if we're processing the statement list in which the last parent is found, scan up
      // from parent
      for (int i = 0; i < statements.length; i++) {
        if (statements[i] == lastParent) {
          for (int j = i - 1; j >= 0; j--) {
            if (!statements[j].processDeclarations(processor, substitutor, lastParent, place)) {
              return false;
            }
          }
          return true;
        }
      }
    }

    for (PyStatement statement : getStatements()) {
      if (!statement.processDeclarations(processor, substitutor, lastParent, place)) {
        return false;
      }
    }
    return true;
  }
}
