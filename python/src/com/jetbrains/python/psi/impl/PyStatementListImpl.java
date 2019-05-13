// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyStatementListImpl extends PyElementImpl implements PyStatementList {
  public PyStatementListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStatementList(this);
  }

  @Override
  public PyStatement[] getStatements() {
    return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getStatementTokens(), PyStatement.EMPTY_ARRAY);
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first.getPsi() instanceof PyStatement && getStatements().length == 1) {
      ASTNode treePrev = getNode().getTreePrev();
      if (treePrev != null && treePrev.getElementType() == TokenType.WHITE_SPACE && !treePrev.textContains('\n')) {
        ASTNode lineBreak = ASTFactory.whitespace("\n");
        treePrev.getTreeParent().replaceChild(treePrev, lineBreak);
      }
    }
    return super.addInternal(first, last, anchor, before);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement childElement = child.getPsi();
    if (childElement instanceof PyStatement && getStatements().length == 1) {
      childElement.replace(PyElementGenerator.getInstance(getProject()).createPassStatement());
      return;
    }
    super.deleteChildInternal(child);
  }
}
