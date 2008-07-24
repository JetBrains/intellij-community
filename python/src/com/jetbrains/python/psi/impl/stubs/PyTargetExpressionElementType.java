package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
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
    return new PyTargetExpressionStubImpl(psi.getName(), parentStub);
  }

  public void serialize(final PyTargetExpressionStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
  }

  public PyTargetExpressionStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    String name = StringRef.toString(dataStream.readName());
    return new PyTargetExpressionStubImpl(name, parentStub);
  }

  public boolean shouldCreateStub(final ASTNode node) {
    final ASTNode functionNode = TreeUtil.findParent(node, PyElementTypes.FUNCTION_DECLARATION);
    final ASTNode qualifierNode = node.findChildByType(PyElementTypes.TARGET_EXPRESSION);
    if (functionNode != null && qualifierNode != null) {
      final ASTNode nameNode = functionNode.findChildByType(PyTokenTypes.IDENTIFIER);
      if (nameNode != null && PyNames.INIT.equals(nameNode.getText())) {
        final ASTNode parameterList = functionNode.findChildByType(PyElementTypes.PARAMETER_LIST);
        assert parameterList != null;
        final ASTNode[] children = parameterList.getChildren(PyElementTypes.FORMAL_PARAMETER_SET);
        if (children.length > 0 && children [0].getText().equals(qualifierNode.getText())) {
          return true;
        }
      }
    }
    return qualifierNode == null;
 }
}
