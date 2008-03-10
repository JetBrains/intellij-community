/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyParameterImpl;
import com.jetbrains.python.psi.stubs.PyParameterStub;

public class PyFormalParameterElementType extends PyStubElementType<PyParameterStub, PyParameter> {
  public PyFormalParameterElementType() {
    super("FORMAL_PARAMETER");
  }

  public PyParameter createPsi(final PyParameterStub stub) {
    return new PyParameterImpl(stub);
  }

  public PyParameterStub createStub(final PyParameter psi, final StubElement parentStub) {
    return new PyParameterStubImpl(psi.getName(), psi.isPositionalContainer(), psi.isKeywordContainer(), parentStub);
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyParameterImpl(node);
  }
}