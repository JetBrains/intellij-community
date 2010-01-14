package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dcheryasov
 */
public class PyExceptPartImpl extends PyElementImpl implements PyExceptPart {
  public PyExceptPartImpl(ASTNode astNode) {
      super(astNode);
  }

  @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
      pyVisitor.visitPyExceptBlock(this);
  }

  public @Nullable PyExpression getExceptClass() {
      return childToPsi(PyElementTypes.EXPRESSIONS, 0);
  }

  public @Nullable PyExpression getTarget() {
      return childToPsi(PyElementTypes.EXPRESSIONS, 1);
  }

  public @NotNull PyStatementList getStatementList() {
      return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return PyUtil.<PyElement>flattenedParens(getTarget());
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return false; 
  }
}
