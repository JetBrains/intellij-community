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
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExceptBlock;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTryExceptStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.06.2005
 * Time: 23:14:57
 * To change this template use File | Settings | File Templates.
 */
public class PyTryExceptStatementImpl extends PyElementImpl implements PyTryExceptStatement {
  private static final TokenSet EXCEPT_BLOCKS = TokenSet.create(PyElementTypes.EXCEPT_BLOCK);

  public PyTryExceptStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTryExceptStatement(this);
  }

  @NotNull
  public PyStatementList getTryStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LISTS, 0);
  }

  @NotNull
  public PyExceptBlock[] getExceptBlocks() {
    return childrenToPsi(EXCEPT_BLOCKS, PyExceptBlock.EMPTY_ARRAY);
  }

  @Nullable
  public PyStatementList getElseStatementList() {
    final ASTNode node = getNode().findChildByType(PyTokenTypes.ELSE_KEYWORD);
    if (node != null) {
      return (PyStatementList)findNextChildOfType(node, PyElementTypes.STATEMENT_LISTS);
    }

    return null;
  }

  @Nullable
  public PyStatementList getFinallyStatementList() {
    final ASTNode node = getNode().findChildByType(PyTokenTypes.FINALLY_KEYWORD);
    if (node != null) {
      return (PyStatementList)findNextChildOfType(node, PyElementTypes.STATEMENT_LISTS);
    }

    return null;
  }

  @Nullable
  private static PsiElement findNextChildOfType(ASTNode node, final TokenSet matchTokens) {
    ASTNode sibling = node.getTreeNext();
    while (sibling != null) {
      if (matchTokens.contains(sibling.getElementType())) {
        return sibling.getPsi();
      }
      sibling = sibling.getTreeNext();
    }
    return null;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final PyStatementList tryStatementList = getTryStatementList();
    if (tryStatementList != lastParent && !tryStatementList.processDeclarations(processor, substitutor, null, place)) {
      return false;
    }

    for (PyExceptBlock block : getExceptBlocks()) {
      if (block != lastParent && !block.processDeclarations(processor, substitutor, null, place)) {
        return false;
      }
    }

    PyStatementList elseStatementList = getElseStatementList();
    if (elseStatementList != null && elseStatementList != lastParent) {
      return elseStatementList.processDeclarations(processor, substitutor, null, place);
    }
    return true;
  }
}
