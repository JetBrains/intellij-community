package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyWithStatementImpl extends PyElementImpl implements PyWithStatement {
  private static final TokenSet WITH_ITEM = TokenSet.create(PyElementTypes.WITH_ITEM);

  public PyWithStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWithStatement(this);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyWithItem[] items = PsiTreeUtil.getChildrenOfType(this, PyWithItem.class);
    List<PyElement> result = new ArrayList<PyElement>();
    if (items != null) {
      for (PyWithItem item : items) {
        PyExpression targetExpression = item.getTargetExpression();
        result.addAll(PyUtil.flattenedParensAndTuples(targetExpression));
      }
    }
    return result;
  }

  public PsiElement getElementNamed(final String the_name) {
    PyElement named_elt = IterHelper.findName(iterateNames(), the_name);
    return named_elt;
  }

  public boolean mustResolveOutside() {
    return false;
  }

  public PyWithItem[] getWithItems() {
    return childrenToPsi(WITH_ITEM, PyWithItem.EMPTY_ARRAY); 
  }
}
