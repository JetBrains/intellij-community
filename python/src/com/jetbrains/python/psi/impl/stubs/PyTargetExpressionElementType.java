package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.DataInputOutputUtil;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.DataInputStream;

/**
 * @author yole
 */
public class PyTargetExpressionElementType extends PyStubElementType<PyTargetExpressionStub, PyTargetExpression> {
  public PyTargetExpressionElementType() {
    super("TARGET_EXPRESSION");
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyTargetExpressionImpl(node);
  }

  public PyTargetExpression createPsi(final PyTargetExpressionStub stub) {
    return new PyTargetExpressionImpl(stub);
  }

  public PyTargetExpressionStub createStub(final PyTargetExpression psi, final StubElement parentStub) {
    return new PyTargetExpressionStubImpl(psi.getName(), parentStub);
  }

  public void serialize(final PyTargetExpressionStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    DataInputOutputUtil.writeNAME(dataStream, stub.getName(), nameStorage);
  }

  public PyTargetExpressionStub deserialize(final DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage)
      throws IOException {
    String name = DataInputOutputUtil.readNAME(dataStream, nameStorage);
    return new PyTargetExpressionStubImpl(name, parentStub);
  }
}
