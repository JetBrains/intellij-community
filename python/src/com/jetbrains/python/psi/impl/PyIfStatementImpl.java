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
 * @author yole
 */
public class PyIfStatementImpl extends PyPartitionedElementImpl implements PyIfStatement {
  public PyIfStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
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

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
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
