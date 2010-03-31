package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import com.jetbrains.python.toolbox.ArrayIterable;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyParameterListImpl extends PyBaseElementImpl<PyParameterListStub> implements PyParameterList {
  public PyParameterListImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyParameterListImpl(final PyParameterListStub stub) {
    super(stub, PyElementTypes.PARAMETER_LIST);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyParameterList(this);
  }

  public PyParameter[] getParameters() {
    return getStubOrPsiChildren(PyElementTypes.PARAMETERS, new PyParameter[0]);
  }

  public void addParameter(final PyNamedParameter param) {
    PsiElement paren = getLastChild();
    if (paren != null && ")".equals(paren.getText())) {
      PyUtil.ensureWritable(this);
      ASTNode beforeWhat = paren.getNode(); // the closing paren will be this
      PyParameter[] params = getParameters();
      PyUtil.addListNode(this, param, beforeWhat, true, params.length == 0);
    }
  }

  public boolean hasPositionalContainer() {
    for (PyParameter parameter: getParameters()) {
      if (parameter instanceof PyNamedParameter && ((PyNamedParameter) parameter).isPositionalContainer()) {
        return true;
      }
    }
    return false;
  }

  public boolean hasKeywordContainer() {
    for (PyParameter parameter: getParameters()) {
      if (parameter instanceof PyNamedParameter && ((PyNamedParameter) parameter).isKeywordContainer()) {
        return true;
      }
    }
    return false;
  }

  public boolean isCompatibleTo(@NotNull PyParameterList another) {
    PyParameter[] parameters = getParameters();
    boolean we_have_starred = hasPositionalContainer();
    boolean we_have_double_starred = hasKeywordContainer();
    final PyParameter[] another_parameters = another.getParameters();
    boolean another_has_starred = another.hasPositionalContainer();
    boolean another_has_double_starred = another.hasKeywordContainer();
    if (parameters.length == another_parameters.length) {
      if (we_have_starred == another_has_starred && we_have_double_starred == another_has_double_starred) {
        return true;
      }
    }
    if (we_have_starred && parameters.length - 1 <= another_parameters.length) {
      if (we_have_double_starred == another_has_double_starred) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return new ArrayIterable<PyElement>(getParameters());
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return false;  // we don't exactly have children to resolve, but if we did...
  }
}
