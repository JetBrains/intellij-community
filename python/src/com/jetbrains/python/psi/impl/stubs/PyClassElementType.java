/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.stubs.PyClassStub;

public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass> {
  public PyClassElementType() {
    super("CLASS_DECLARATION");
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyClassImpl(node);
  }

  public PyClass createPsi(final PyClassStub stub) {
    return new PyClassImpl(stub);
  }

  public PyClassStub createStub(final PyClass psi, final StubElement parentStub) {
    return new PyClassStubImpl(psi.getName(), parentStub);
  }
}