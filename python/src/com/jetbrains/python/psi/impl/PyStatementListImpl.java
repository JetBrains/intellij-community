package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;

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
}
