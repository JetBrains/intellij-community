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
import com.intellij.openapi.diagnostic.Logger;
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
 * Time: 21:08:06
 * To change this template use File | Settings | File Templates.
 */
public class PyIfStatementImpl extends PyPartitionedElementImpl implements PyIfStatement {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.psi.impl.PyIfStatementImpl");

  public PyIfStatementImpl(ASTNode astNode) {
      super(astNode);
  }

  @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
      pyVisitor.visitPyIfStatement(this);
  }

  @NotNull
  public PyIfPart getIfPart() {
    return (PyIfPart)getPartNotNull(PyElementTypes.IF_PART_IF);
  }

  @NotNull
  public PyIfPart[] getElifParts() {
    return childrenToPsi(PyElementTypes.ELIFS, PyIfPart.EMPTY_ARRAY);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @Override public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                                 @NotNull ResolveState substitutor,
                                                 PsiElement lastParent,
                                                 @NotNull PsiElement place)
    {
      if (lastParent != null) {
          return true;
      }
      for (PyStatementPart part : getParts()) {
        PyStatementList stmtList = part.getStatementList();
        if (stmtList != null && !stmtList.processDeclarations(processor, substitutor, lastParent, place)) {
            return false;
        }
      }

      /*

      PyStatementList[] statementLists = getStatementLists();
      for (PyStatementList statementList: statementLists) {
          if (!statementList.processDeclarations(processor, substitutor, lastParent, place)) {
              return false;
          }
      }
      PyStatementList elseList = getElseStatementList();
      //noinspection RedundantIfStatement
      if (elseList != null && !elseList.processDeclarations(processor, substitutor, lastParent, place)) {
          return false;
      }
      */
      return true;
    }

}
