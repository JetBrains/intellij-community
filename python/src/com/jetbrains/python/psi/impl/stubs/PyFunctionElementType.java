/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.stubs.PyFunctionStub;

public class PyFunctionElementType extends PyStubElementType<PyFunctionStub, PyFunction> {
  public PyFunctionElementType() {
    super("FUNCTION_DECLARATION");
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyFunctionImpl(node);
  }

  public PyFunction createPsi(final PyFunctionStub stub) {
    return new PyFunctionImpl(stub);
  }

  public PyFunctionStub createStub(final PyFunction psi, final StubElement parentStub) {
    return new PyFunctionStubImpl(psi.getName(), parentStub);
  }
}