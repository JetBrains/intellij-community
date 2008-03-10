/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyParameterListImpl;
import com.jetbrains.python.psi.stubs.PyParameterListStub;

public class PyParameterListElementType extends PyStubElementType<PyParameterListStub, PyParameterList> {
  public PyParameterListElementType() {
    super("PARAMETER_LIST");
  }

  public PyParameterList createPsi(final PyParameterListStub stub) {
    return new PyParameterListImpl(stub);
  }

  public PyParameterListStub createStub(final PyParameterList psi, final StubElement parentStub) {
    return new PyParameterListStubImpl(parentStub);
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyParameterListImpl(node);
  }
}