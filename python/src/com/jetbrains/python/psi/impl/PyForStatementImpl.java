package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class PyForStatementImpl extends PyPartitionedElementImpl implements PyForStatement {
  public PyForStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyForStatement(this);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @NotNull
  public PyForPart getForPart() {
    return (PyForPart)getPartNotNull(PyElementTypes.FOR_PART);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    PyForPart forPart = getForPart();
    final PyExpression target = forPart.getTarget();
    if (target != null && target != lastParent && !target.processDeclarations(processor, substitutor, null, place)) {
      return false;
    }

    final PyStatementList statementList = forPart.getStatementList();
    if (statementList != null && statementList != lastParent && !statementList.processDeclarations(processor, substitutor, null, place)) {
      return false;
    }
    PyElsePart elsePart = getElsePart();
    if (elsePart != null) {
      PyStatementList elseList = elsePart.getStatementList();
      if (elseList != null && elseList != lastParent) {
        return elseList.processDeclarations(processor, substitutor, null, place);
      }
    }
    return true;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyExpression tgt = getForPart().getTarget();
    if (tgt instanceof PyReferenceExpression) return Collections.<PyElement>singleton(tgt);
    else {
      return PyUtil.flattenedParens(new PyElement[]{tgt});
    }
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return false; 
  }
}
