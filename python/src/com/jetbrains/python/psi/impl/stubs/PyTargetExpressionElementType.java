package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.psi.stubs.PropertyStubStorage;
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
    final String name = psi.getName();
    final PyExpression assignedValue = psi.findAssignedValue();
    PropertyStubStorage prop = PropertyStubStorage.fromCall(assignedValue);
    if (prop != null) {
      return new PyTargetExpressionStubImpl(name, prop, parentStub);
    }
    else {
      final PyQualifiedName initializer = assignedValue instanceof PyReferenceExpression
                                          ? ((PyReferenceExpression) assignedValue).asQualifiedName()
                                          : null;
      return new PyTargetExpressionStubImpl(name, initializer, parentStub);
    }
  }

  private static final int SIMPLE = 0; // stream stores a PyTargetExpressionStubImpl
  private static final int PROPERTY = 1; // stream stores a PyTargetExpressionPropertyStubImpl

  public void serialize(final PyTargetExpressionStub stub, final StubOutputStream stream)
      throws IOException {
    stream.writeName(stub.getName());
    PropertyStubStorage prop = stub.getPropertyPack();
    if (prop != null) {
      stream.writeVarInt(PROPERTY);
      prop.serialize(stream);
    }
    else {
      stream.writeVarInt(SIMPLE);
      PyQualifiedName.serialize(stub.getInitializer(), stream);
    }
  }

  public PyTargetExpressionStub deserialize(final StubInputStream stream, final StubElement parentStub)
      throws IOException {
    String name = StringRef.toString(stream.readName());
    int code = stream.readVarInt();
    if (code == SIMPLE) {
      PyQualifiedName initializer = PyQualifiedName.deserialize(stream);
      return new PyTargetExpressionStubImpl(name, initializer, parentStub);
    }
    else if (code == PROPERTY) {
      PropertyStubStorage prop = PropertyStubStorage.deserialize(stream);
      return new PyTargetExpressionStubImpl(name, prop, parentStub);
    }
    else {
      assert false : "Unknown code in stream: " + code;
      return null; // to keep inspections safe
    }
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
