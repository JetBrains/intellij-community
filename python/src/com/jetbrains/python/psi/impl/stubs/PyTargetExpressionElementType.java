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
      PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.Other;
      PyQualifiedName initializer = null;
      if (assignedValue instanceof PyReferenceExpression) {
        initializerType = PyTargetExpressionStub.InitializerType.ReferenceExpression;
        initializer = ((PyReferenceExpression) assignedValue).asQualifiedName();
      }
      else if (assignedValue instanceof PyCallExpression) {
        initializerType = PyTargetExpressionStub.InitializerType.CallExpression;
        final PyExpression callee = ((PyCallExpression)assignedValue).getCallee();
        if (callee instanceof PyReferenceExpression) {
          initializer = ((PyReferenceExpression) callee).asQualifiedName();
        }
      }
      return new PyTargetExpressionStubImpl(name, initializerType, initializer, parentStub);
    }
  }

  public void serialize(final PyTargetExpressionStub stub, final StubOutputStream stream)
      throws IOException {
    stream.writeName(stub.getName());
    stream.writeVarInt(stub.getInitializerType().getIndex());
    if (stub.getInitializerType() == PyTargetExpressionStub.InitializerType.Property) {
      final PropertyStubStorage propertyPack = stub.getPropertyPack();
      assert propertyPack != null;
      propertyPack.serialize(stream);
    }
    else {
      PyQualifiedName.serialize(stub.getInitializer(), stream);
    }
  }

  public PyTargetExpressionStub deserialize(final StubInputStream stream, final StubElement parentStub)
      throws IOException {
    String name = StringRef.toString(stream.readName());
    PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.fromIndex(stream.readVarInt());
    if (initializerType == PyTargetExpressionStub.InitializerType.Property) {
      PropertyStubStorage prop = PropertyStubStorage.deserialize(stream);
      return new PyTargetExpressionStubImpl(name, prop, parentStub);
    }
    PyQualifiedName initializer = PyQualifiedName.deserialize(stream);
    return new PyTargetExpressionStubImpl(name, initializerType, initializer, parentStub);
  }

  public boolean shouldCreateStub(final ASTNode node) {
    final ASTNode functionNode = TreeUtil.findParent(node, PyElementTypes.FUNCTION_DECLARATION);
    final ASTNode qualifierNode = node.findChildByType(PyElementTypes.REFERENCE_EXPRESSION);
    if (functionNode != null && qualifierNode != null) {
      final ASTNode parameterList = functionNode.findChildByType(PyElementTypes.PARAMETER_LIST);
      assert parameterList != null;
      final ASTNode[] children = parameterList.getChildren(PyElementTypes.FORMAL_PARAMETER_SET);
      if (children.length > 0 && children [0].getText().equals(qualifierNode.getText())) {
        return true;
      }
    }
    return functionNode == null && qualifierNode == null;
 }

}
