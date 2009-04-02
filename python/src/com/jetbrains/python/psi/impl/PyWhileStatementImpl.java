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
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 15:47:44
 * To change this template use File | Settings | File Templates.
 */
public class PyWhileStatementImpl extends PyPartitionedElementImpl implements PyWhileStatement {
  public PyWhileStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWhileStatement(this);
  }

  @NotNull
  public PyWhilePart getWhilePart() {
    return (PyWhilePart)getPartNotNull(PyElementTypes.WHILE_PART);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @Override public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                                 @NotNull ResolveState substitutor,
                                                 PsiElement lastParent,
                                                 @NotNull PsiElement place)
    {
      if (lastParent != null) return true;

      for (PyStatementPart part : getParts()) {
        PyStatementList stmtList = part.getStatementList();
        if (stmtList != null) {
            return stmtList.processDeclarations(processor, substitutor, null, place);
        }
      }

      /*
      final PyStatementList whileStmts = getWhilePart().getStatementList();
      if (whileStmts != null && !whileStmts.processDeclarations(processor, substitutor, null, place)) {
        return false;
      }
      final PyElsePart elsePart = getElsePart();
      if (elsePart != null) {
        PyStatementList elseList = elsePart.getStatementList();
        if (elseList != null) {
            return elseList.processDeclarations(processor, substitutor, null, place);
        }
      }
      */
      return true;
    }
}
