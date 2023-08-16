package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyTypeParameterListImpl extends PyElementImpl implements PyTypeParameterList {
  public PyTypeParameterListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTypeParameterList(this);
  }


  @Override
  @NotNull
  public List<PyTypeParameter> getTypeParameters() {
    return findChildrenByType(PyElementTypes.TYPE_PARAMETER);
  }
}
