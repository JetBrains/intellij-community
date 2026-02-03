// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyStatementListImpl extends PyLazyParseablePsiElement implements PyStatementList {

  public PyStatementListImpl(@NotNull IElementType type, @Nullable CharSequence buffer) {
    super(type, buffer);
  }

  public PyStatementListImpl(@Nullable CharSequence buffer) {
    super(PyElementTypes.STATEMENT_LIST, buffer);
  }

  @Override
  protected void acceptPyVisitor(@NotNull PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStatementList(this);
  }

  @Override
  public PyStatement[] getStatements() {
    return childrenToPsi(PythonDialectsTokenSetProvider.getInstance().getStatementTokens(), PyStatement.EMPTY_ARRAY);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
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

  @Override
  public String toString() {
    return "PyStatementList";
  }
}