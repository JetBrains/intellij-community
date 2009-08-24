package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.PyElementVisitor;
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

  protected PyTupleParameterImpl(final PyTupleParameterStub stub, final IStubElementType nodeType) {
    super(stub, nodeType);
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
