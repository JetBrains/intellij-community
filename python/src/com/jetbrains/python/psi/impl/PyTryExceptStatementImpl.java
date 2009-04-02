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
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.06.2005
 * Time: 23:14:57
 * To change this template use File | Settings | File Templates.
 */
public class PyTryExceptStatementImpl extends PyPartitionedElementImpl implements PyTryExceptStatement {
  private static final TokenSet EXCEPT_BLOCKS = TokenSet.create(PyElementTypes.EXCEPT_PART);

  public PyTryExceptStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTryExceptStatement(this);
  }

  @NotNull
  public PyExceptPart[] getExceptParts() {
    return childrenToPsi(EXCEPT_BLOCKS, PyExceptPart.EMPTY_ARRAY);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @NotNull
  public PyTryPart getTryPart() {
    return (PyTryPart)getPartNotNull(PyElementTypes.TRY_PART);
  }


  public PyFinallyPart getFinallyPart() {
    return (PyFinallyPart)getPart(PyElementTypes.FINALLY_PART);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    /*
    final PyStatementList tryStatementList = getTryPart().getStatementList();
    if (tryStatementList != null && tryStatementList != lastParent && !tryStatementList.processDeclarations(processor, substitutor, null, place)) {
      return false;
    }
    */

    for (PyStatementPart part : /*getExceptParts()*/ getParts()) {
      if (part != lastParent && !part.processDeclarations(processor, substitutor, null, place)) {
        return false;
      }
    }

    /*
    final PyElsePart elsePart = getElsePart();
    if (elsePart != null) {
      PyStatementList elseStatementList = elsePart.getStatementList();
      if (elseStatementList != null && elseStatementList != lastParent) {
        return elseStatementList.processDeclarations(processor, substitutor, null, place);
      }
    }
    */
    return true;
  }
}
