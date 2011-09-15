package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author dcheryasov
 */
public class PyExceptPartImpl extends PyBaseElementImpl<PyExceptPartStub> implements PyExceptPart {
  public PyExceptPartImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyExceptPartImpl(PyExceptPartStub stub) {
    super(stub, PyElementTypes.EXCEPT_PART);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyExceptBlock(this);
  }

  @Nullable
  public PyExpression getExceptClass() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Nullable
  public PyExpression getTarget() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
  }

  @NotNull
  public PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return new ArrayList<PyElement>(PyUtil.flattenedParensAndStars(getTarget()));
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return false;
  }
}
