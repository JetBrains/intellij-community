package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;

import java.io.IOException;

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
    final PyExpression assignedValue = psi.findAssignedValue();
    final PyQualifiedName initializer = assignedValue instanceof PyReferenceExpression
                                        ? ((PyReferenceExpression) assignedValue).asQualifiedName()
                                        : null;
    return new PyTargetExpressionStubImpl(psi.getName(), initializer, parentStub);
  }

  public void serialize(final PyTargetExpressionStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
    PyQualifiedName.serialize(stub.getInitializer(), dataStream);
  }

  public PyTargetExpressionStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    String name = StringRef.toString(dataStream.readName());
    PyQualifiedName initializer = PyQualifiedName.deserialize(dataStream);
    return new PyTargetExpressionStubImpl(name, initializer, parentStub);
  }

  public boolean shouldCreateStub(final ASTNode node) {
    final ASTNode functionNode = TreeUtil.findParent(node, PyElementTypes.FUNCTION_DECLARATION);
    final ASTNode qualifierNode = node.findChildByType(PyElementTypes.TARGET_EXPRESSION);
    if (functionNode != null && qualifierNode != null) {
      final ASTNode parameterList = functionNode.findChildByType(PyElementTypes.PARAMETER_LIST);
      assert parameterList != null;
      final ASTNode[] children = parameterList.getChildren(PyElementTypes.FORMAL_PARAMETER_SET);
      if (children.length > 0 && children [0].getText().equals(qualifierNode.getText())) {
        return true;
      }
    }
    return qualifierNode == null;
 }
}
