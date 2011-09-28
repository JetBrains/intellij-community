package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a tuple parameter as stubbed element.
 */
public class PyTupleParameterImpl extends PyPresentableElementImpl<PyTupleParameterStub> implements PyTupleParameter {
  
  public PyTupleParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyTupleParameterImpl(PyTupleParameterStub stub) {
    super(stub, PyElementTypes.TUPLE_PARAMETER);
  }

  public PyNamedParameter getAsNamed() {
    return null;  // we're not named
  }

  public PyTupleParameter getAsTuple() {
    return this;
  }

  public PyExpression getDefaultValue() {
    ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    if (nodes.length > 0) {
      return (PyExpression)nodes[0].getPsi();
    }
    return null;
  }

  public boolean hasDefaultValue() {
    final PyTupleParameterStub stub = getStub();
    if (stub != null) {
      return stub.hasDefaultValue();
    }
    return getDefaultValue() != null;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Can't rename a tuple parameter to '" + name +"'"); 
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTupleParameter(this);
  }

  @NotNull
  public PyParameter[] getContents() {
    return getStubOrPsiChildren(PyElementTypes.PARAMETERS, new PyParameter[0]);
  }
}
