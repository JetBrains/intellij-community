package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IStubElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.stubs.PyTypeParameterListStub;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyTypeParameterListImpl extends PyBaseElementImpl<PyTypeParameterListStub> implements PyTypeParameterList {
  public PyTypeParameterListImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyTypeParameterListImpl(PyTypeParameterListStub stub) {
    this(stub, PyStubElementTypes.PARAMETER_LIST);
  }

  public PyTypeParameterListImpl(PyTypeParameterListStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTypeParameterList(this);
  }


  @Override
  @NotNull
  public List<PyTypeParameter> getTypeParameters() {
    return List.of(getStubOrPsiChildren(PyStubElementTypes.TYPE_PARAMETER, new PyTypeParameter[0]));
  }
}
